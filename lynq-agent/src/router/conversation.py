from __future__ import annotations

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Header, HTTPException

from agent.graph import AgentError
from db.errors import (
    ConversationExhausted,
    ConversationNotFound,
    ConversationNotOwned,
    TurnAlreadyRunning,
)
from model.conversation import (
    AppliedRequest,
    AppliedResponse,
    ConversationView,
    CreateConversationRequest,
    CreateConversationResponse,
    TurnRequest,
    TurnResponse,
)
from response import GlobalRestResponse
from router.dependencies import get_conversation_service
from service.conversation_service import ConversationService

log = logging.getLogger(__name__)

router = APIRouter()

Service = Annotated[ConversationService, Depends(get_conversation_service)]
RequestUuid = Annotated[str, Header(alias="lynq-request-uuid")]
UserId = Annotated[str, Header(alias="user-id")]


def _translate(exc: Exception) -> HTTPException:
    if isinstance(exc, ConversationNotFound):
        return HTTPException(status_code=404, detail="Conversation not found")
    if isinstance(exc, ConversationNotOwned):
        return HTTPException(
            status_code=403, detail="The conversation belongs to another user"
        )
    if isinstance(exc, TurnAlreadyRunning):
        return HTTPException(
            status_code=409, detail="A turn is already running on this conversation"
        )
    if isinstance(exc, ConversationExhausted):
        return HTTPException(
            status_code=409, detail="The conversation ran out of turns"
        )
    return HTTPException(status_code=500, detail=str(exc))


@router.post("/conversation", status_code=201)
async def create_conversation(
    body: CreateConversationRequest,
    service: Service,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
) -> GlobalRestResponse[CreateConversationResponse]:
    log.info(
        "message= Started conversation creation, user_id=%s, job_id=%s",
        user_id,
        body.job.id,
    )
    created = await service.create(user_id, body)
    return GlobalRestResponse(data=created)


@router.post(
    "/conversation/{conversation_id}/turn",
    responses={
        403: {"description": "The conversation belongs to another user."},
        404: {"description": "No such conversation."},
        409: {"description": "A turn is running, or the conversation is exhausted."},
        502: {"description": "The LLM failed or returned something that does not validate."},
    },
)
async def turn(
    conversation_id: str,
    body: TurnRequest,
    service: Service,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
) -> GlobalRestResponse[TurnResponse]:
    log.info(
        "message= Started turn, conversation_id=%s, user_id=%s",
        conversation_id,
        user_id,
    )
    try:
        answered = await service.turn(
            conversation_id=conversation_id,
            user_id=user_id,
            message=body.message,
            turn_key=body.turn_key,
            request_uuid=lynq_request_uuid,
        )
    except (
        ConversationNotFound,
        ConversationNotOwned,
        TurnAlreadyRunning,
        ConversationExhausted,
    ) as exc:
        raise _translate(exc) from exc
    except AgentError as exc:
        raise HTTPException(status_code=502, detail=f"LLM request failed: {exc}") from exc

    return GlobalRestResponse(data=answered)


@router.get(
    "/conversation/{conversation_id}",
    responses={
        403: {"description": "The conversation belongs to another user."},
        404: {"description": "No such conversation."},
    },
)
async def get_conversation(
    conversation_id: str,
    service: Service,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
) -> GlobalRestResponse[ConversationView]:
    try:
        view = await service.view(conversation_id, user_id)
    except (ConversationNotFound, ConversationNotOwned) as exc:
        raise _translate(exc) from exc
    return GlobalRestResponse(data=view)


@router.patch(
    "/conversation/{conversation_id}/applied",
    responses={
        403: {"description": "The conversation belongs to another user."},
        404: {"description": "No such conversation."},
    },
)
async def mark_applied(
    conversation_id: str,
    body: AppliedRequest,
    service: Service,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
) -> GlobalRestResponse[AppliedResponse]:
    try:
        status = await service.mark_applied(
            conversation_id=conversation_id,
            user_id=user_id,
            applied_resume_id=body.applied_resume_id,
            score_after=body.score_after,
        )
    except (ConversationNotFound, ConversationNotOwned) as exc:
        raise _translate(exc) from exc
    return GlobalRestResponse(data=AppliedResponse(status=status))
