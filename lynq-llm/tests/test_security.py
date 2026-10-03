"""Tests for resolving a caller from the Authorization header."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, patch

import httpx
from fastapi import HTTPException

from security import (
    IamClient,
    Principal,
    get_iam_client,
    require_internal_caller,
    require_principal,
    reset_iam_client,
)

_BEARER = "Bearer access-token"
_REQUEST_UUID = "req-123"
_USER_ID = "11111111-1111-1111-1111-111111111111"

_USER_INFO = {
    "success": True,
    "data": {
        "id": _USER_ID,
        "username": "janedoe",
        "email": "jane@lynq.com",
        "roles": ["R_CANDIDATE"],
    },
}


def _response(status_code: int, json_body=None) -> httpx.Response:
    return httpx.Response(
        status_code=status_code,
        json=json_body if json_body is not None else {},
        request=httpx.Request("GET", "http://lynq-iam/auth/user-info"),
    )


def _iam_returning(response: httpx.Response):
    return patch.object(httpx.AsyncClient, "get", AsyncMock(return_value=response))


class PrincipalTests(unittest.TestCase):

    def test_has_role_matches_the_prefixed_authority(self) -> None:
        principal = Principal(id=_USER_ID, roles=["R_COMPANY"])

        self.assertTrue(principal.has_role("COMPANY"))
        self.assertFalse(principal.has_role("CANDIDATE"))


class IamClientTests(unittest.IsolatedAsyncioTestCase):

    def setUp(self) -> None:
        self.client = IamClient(base_url="http://lynq-iam/lynq-iam", timeout=1.0)

    async def test_returns_the_user_lynq_iam_resolved_the_token_to(self) -> None:
        with _iam_returning(_response(200, _USER_INFO)):
            principal = await self.client.user_info(_BEARER, _REQUEST_UUID)

        self.assertEqual(principal.id, _USER_ID)
        self.assertEqual(principal.username, "janedoe")
        self.assertEqual(principal.email, "jane@lynq.com")
        self.assertEqual(principal.roles, ["R_CANDIDATE"])

    async def test_rejects_a_token_lynq_iam_refuses(self) -> None:
        for status_code in (401, 403):
            with self.subTest(status_code=status_code):
                with _iam_returning(_response(status_code)):
                    with self.assertRaises(HTTPException) as raised:
                        await self.client.user_info(_BEARER, _REQUEST_UUID)

                self.assertEqual(raised.exception.status_code, 401)

    async def test_reports_lynq_iam_being_down_as_service_unavailable(self) -> None:
        with patch.object(
            httpx.AsyncClient, "get", AsyncMock(side_effect=httpx.ConnectError("down"))
        ):
            with self.assertRaises(HTTPException) as raised:
                await self.client.user_info(_BEARER, _REQUEST_UUID)

        self.assertEqual(raised.exception.status_code, 503)

    async def test_reports_an_unexpected_lynq_iam_status_as_service_unavailable(self) -> None:
        with _iam_returning(_response(500)):
            with self.assertRaises(HTTPException) as raised:
                await self.client.user_info(_BEARER, _REQUEST_UUID)

        self.assertEqual(raised.exception.status_code, 503)

    async def test_rejects_an_envelope_that_names_nobody(self) -> None:
        with _iam_returning(_response(200, {"success": True, "data": None})):
            with self.assertRaises(HTTPException) as raised:
                await self.client.user_info(_BEARER, _REQUEST_UUID)

        self.assertEqual(raised.exception.status_code, 401)

    async def test_ignores_roles_that_are_not_strings(self) -> None:
        body = {"success": True, "data": {"id": _USER_ID, "roles": ["R_COMPANY", 7]}}

        with _iam_returning(_response(200, body)):
            principal = await self.client.user_info(_BEARER, _REQUEST_UUID)

        self.assertEqual(principal.roles, ["R_COMPANY"])


class RequirePrincipalTests(unittest.IsolatedAsyncioTestCase):

    def tearDown(self) -> None:
        reset_iam_client()

    async def test_refuses_a_request_without_an_authorization_header(self) -> None:
        for header in (None, "   "):
            with self.subTest(header=header):
                with self.assertRaises(HTTPException) as raised:
                    await require_principal(_REQUEST_UUID, header)

                self.assertEqual(raised.exception.status_code, 401)
                self.assertEqual(
                    raised.exception.detail, "Missing Authorization header"
                )

    async def test_resolves_the_caller_against_lynq_iam(self) -> None:
        with _iam_returning(_response(200, _USER_INFO)):
            principal = await require_principal(_REQUEST_UUID, _BEARER)

        self.assertEqual(principal.id, _USER_ID)

    def test_the_iam_client_is_built_once(self) -> None:
        reset_iam_client()

        client = get_iam_client()

        self.assertIs(get_iam_client(), client)


class RequireInternalCallerTests(unittest.TestCase):

    def test_accepts_the_configured_internal_token(self) -> None:
        with patch.dict("os.environ", {"LYNQ_INTERNAL_TOKEN": "s3cret"}):
            require_internal_caller("s3cret")

    def test_refuses_a_wrong_missing_or_unconfigured_token(self) -> None:
        cases = [("s3cret", "other"), ("s3cret", None), ("", "s3cret")]
        for expected, provided in cases:
            with self.subTest(expected=expected, provided=provided):
                with patch.dict("os.environ", {"LYNQ_INTERNAL_TOKEN": expected}):
                    with self.assertRaises(HTTPException) as raised:
                        require_internal_caller(provided)

                self.assertEqual(raised.exception.status_code, 401)


if __name__ == "__main__":
    unittest.main()
