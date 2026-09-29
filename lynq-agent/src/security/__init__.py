"""Resolving the caller of a route from the Authorization header it arrived with.

The DMZ routes used to trust a ``user-id`` header: whoever reached the service
named the user they were acting for, and a conversation is owned by that user.
The identity now comes from the access token instead, resolved against lynq-iam
exactly as lynq-app-backend resolves it, so the token is the only thing that can
name a caller — and the only thing that can reach somebody else's conversation.
"""

from __future__ import annotations

import logging
from typing import Annotated, Optional

import httpx
from fastapi import Depends, Header, HTTPException
from pydantic import BaseModel

from config import get_settings
from middleware.request_uuid import REQUEST_UUID_HEADER

log = logging.getLogger(__name__)

USER_INFO_PATH = "/auth/user-info"
AUTHORIZATION_HEADER = "Authorization"

ROLE_PREFIX = "R_"

CANDIDATE_ROLE = "CANDIDATE"

MISSING_AUTHORIZATION_ERROR = "Missing Authorization header"
INVALID_TOKEN_ERROR = "Invalid or expired access token"
IAM_UNAVAILABLE_ERROR = "Authentication service is unavailable"
NOT_A_CANDIDATE_ERROR = "Only users of type CANDIDATE can perform this action"


class Principal(BaseModel):
    """The user a request is acting as, as lynq-iam describes them."""

    id: str
    username: Optional[str] = None
    email: Optional[str] = None
    roles: list[str] = []

    def has_role(self, role: str) -> bool:
        return (ROLE_PREFIX + role) in self.roles


class IamClient:

    def __init__(
        self, base_url: str | None = None, timeout: float | None = None
    ) -> None:
        settings = get_settings()
        url = base_url or settings.lynq_iam_url
        self._url = url.rstrip("/") + USER_INFO_PATH
        self._timeout = (
            timeout if timeout is not None else settings.lynq_iam_timeout_seconds
        )

    async def user_info(self, authorization: str, request_uuid: str) -> Principal:
        headers = {
            AUTHORIZATION_HEADER: authorization,
            REQUEST_UUID_HEADER: request_uuid,
        }
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.get(self._url, headers=headers)
        except httpx.HTTPError as exc:
            log.error("message= lynq-iam could not be reached", exc_info=exc)
            raise HTTPException(
                status_code=503, detail=IAM_UNAVAILABLE_ERROR
            ) from exc

        if response.status_code in (401, 403):
            raise HTTPException(status_code=401, detail=INVALID_TOKEN_ERROR)
        if response.status_code >= 400:
            log.error(
                "message= lynq-iam answered the user-info lookup with %s",
                response.status_code,
            )
            raise HTTPException(status_code=503, detail=IAM_UNAVAILABLE_ERROR)

        try:
            data = (response.json() or {}).get("data")
        except ValueError as exc:
            log.error("message= lynq-iam returned a non-JSON body", exc_info=exc)
            raise HTTPException(
                status_code=503, detail=IAM_UNAVAILABLE_ERROR
            ) from exc

        if not isinstance(data, dict) or not data.get("id"):
            raise HTTPException(status_code=401, detail=INVALID_TOKEN_ERROR)

        roles = data.get("roles")
        return Principal(
            id=data["id"],
            username=data.get("username"),
            email=data.get("email"),
            roles=[role for role in roles if isinstance(role, str)]
            if isinstance(roles, list)
            else [],
        )


_iam_client: IamClient | None = None


def get_iam_client() -> IamClient:
    global _iam_client
    if _iam_client is None:
        _iam_client = IamClient()
    return _iam_client


def reset_iam_client() -> None:
    global _iam_client
    _iam_client = None


async def require_principal(
    lynq_request_uuid: Annotated[str, Header(alias=REQUEST_UUID_HEADER)],
    authorization: Annotated[
        Optional[str], Header(alias=AUTHORIZATION_HEADER)
    ] = None,
) -> Principal:
    if not authorization or not authorization.strip():
        raise HTTPException(status_code=401, detail=MISSING_AUTHORIZATION_ERROR)
    return await get_iam_client().user_info(authorization, lynq_request_uuid)


CallerPrincipal = Annotated[Principal, Depends(require_principal)]


def require_candidate(principal: CallerPrincipal) -> Principal:
    if not principal.has_role(CANDIDATE_ROLE):
        raise HTTPException(status_code=403, detail=NOT_A_CANDIDATE_ERROR)
    return principal


CandidatePrincipal = Annotated[Principal, Depends(require_candidate)]
