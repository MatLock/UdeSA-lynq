from __future__ import annotations

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, Header

from client.lynq_ml_client import get_lynq_ml_client
from config import get_settings
from db.session import get_session_factory
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
from service.conversation_service import ConversationService

log = logging.getLogger(__name__)

router = APIRouter()

_ERRORS = {
    403: {"description": "The caller is not the owner of the conversation."},
    404: {"description": "The conversation does not exist."},
    409: {"description": "A turn is running, the conversation is closed, or it was already applied."},
    502: {"description": "The agent could not finish the turn."},
}


def get_conversation_service() -> ConversationService:
    return ConversationService(
        session_factory=get_session_factory(),
        lynq_ml_client=get_lynq_ml_client(),
        settings=get_settings(),
    )


Service = Annotated[ConversationService, Depends(get_conversation_service)]
RequestUuid = Annotated[str, Header(alias="lynq-request-uuid")]
UserId = Annotated[str, Header(alias="user-id")]


@router.post("/conversation", responses=_ERRORS)
async def create_conversation(
    body: CreateConversationRequest,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
    service: Service,
) -> GlobalRestResponse[CreateConversationResponse]:
    log.info(
        "message= Started conversation creation, user_id=%s, job_id=%s",
        user_id,
        body.job.id,
    )
    return GlobalRestResponse(
        data=await service.create(body, lynq_request_uuid, user_id)
    )


@router.post("/conversation/{conversation_id}/turn", responses=_ERRORS)
async def take_turn(
    conversation_id: str,
    body: TurnRequest,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
    service: Service,
) -> GlobalRestResponse[TurnResponse]:
    log.info(
        "message= Started turn, user_id=%s, conversation_id=%s, turn_key=%s",
        user_id,
        conversation_id,
        body.turn_key,
    )
    return GlobalRestResponse(data=await service.turn(conversation_id, body, user_id))


@router.get("/conversation/{conversation_id}", responses=_ERRORS)
async def get_conversation(
    conversation_id: str,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
    service: Service,
) -> GlobalRestResponse[ConversationView]:
    return GlobalRestResponse(data=await service.view(conversation_id, user_id))


@router.patch("/conversation/{conversation_id}/applied", responses=_ERRORS)
async def mark_applied(
    conversation_id: str,
    body: AppliedRequest,
    lynq_request_uuid: RequestUuid,
    user_id: UserId,
    service: Service,
) -> GlobalRestResponse[AppliedResponse]:
    log.info(
        "message= Marking a conversation as applied, user_id=%s, conversation_id=%s",
        user_id,
        conversation_id,
    )
    return GlobalRestResponse(
        data=await service.mark_applied(conversation_id, body, user_id)
    )
