from __future__ import annotations

import logging

from agent.context import TurnContext
from agent.editor import ResumeEditor
from agent.graph import AgentError, TurnOutcome, run_turn
from agent.pricing import prices_for
from client import LynqMlClient
from config import Settings
from db.models import ConversationStatus
from db.repository import ConversationRepository, TurnClaim, TurnReplay
from llm import build_model, selected_model_id, selected_provider
from model.conversation import (
    ConversationMessage,
    ConversationVersion,
    ConversationView,
    CreateConversationRequest,
    CreateConversationResponse,
    TurnResponse,
)
from prompt.resume_tailor import render_greeting

log = logging.getLogger(__name__)


class ConversationService:

    def __init__(
        self,
        repository: ConversationRepository,
        ml_client: LynqMlClient,
        settings: Settings,
        turn_runner=run_turn,
        model_builder=build_model,
    ) -> None:
        self._repository = repository
        self._ml_client = ml_client
        self._settings = settings
        self._turn_runner = turn_runner
        self._model_builder = model_builder

    async def create(
        self, user_id: str, request: CreateConversationRequest
    ) -> CreateConversationResponse:
        provider = selected_provider().value
        model_id = selected_model_id()
        input_price, output_price = prices_for(provider, model_id)

        job = request.job.model_dump()
        greeting = render_greeting(
            request.language, title=request.job.title, company=request.job.company
        )

        conversation = await self._repository.create_conversation(
            user_id=user_id,
            job_id=request.job.id,
            job_snapshot=job,
            base_resume_id=request.base_resume_id,
            base_resume=request.base_resume.model_dump(),
            language=request.language,
            status=ConversationStatus.AWAITING_CONFIRMATION,
            llm_provider=provider,
            llm_model=model_id,
            input_price_per_1m=input_price,
            output_price_per_1m=output_price,
            max_turns=self._settings.max_turns,
            max_steps=self._settings.max_steps,
            score_before=request.score_before,
            greeting=greeting,
        )

        log.info(
            "message= Created tailoring conversation, conversation_id=%s, "
            "job_id=%s, base_resume_id=%s, provider=%s, model=%s",
            conversation.id,
            request.job.id,
            request.base_resume_id,
            provider,
            model_id,
        )

        return CreateConversationResponse(
            conversation_id=conversation.id,
            greeting=greeting,
            status=conversation.status,
            turns_left=conversation.max_turns,
        )

    async def turn(
        self,
        *,
        conversation_id: str,
        user_id: str,
        message: str,
        turn_key: str,
        request_uuid: str,
    ) -> TurnResponse:
        claimed = await self._repository.claim_turn(
            conversation_id=conversation_id,
            user_id=user_id,
            message=message,
            turn_key=turn_key,
            turn_timeout_seconds=self._settings.turn_timeout_seconds,
            history_pairs=self._settings.history_pairs,
        )

        if isinstance(claimed, TurnReplay):
            log.info(
                "message= Replayed an already answered turn, conversation_id=%s",
                conversation_id,
            )
            return _as_turn_response(claimed)

        try:
            outcome = await self._run(claimed, request_uuid)
        except AgentError as exc:
            await self._repository.fail_turn(claim=claimed, error=str(exc))
            log.error(
                "message= Turn failed, the conversation stays usable, "
                "conversation_id=%s",
                conversation_id,
                exc_info=exc,
            )
            raise

        replay = await self._repository.finish_turn(
            claim=claimed,
            reply=outcome.reply,
            warnings=outcome.warnings,
            resume=outcome.resume,
            changes=outcome.changes,
            spans=outcome.spans,
            job_requirements=outcome.job_requirements,
        )

        log.info(
            "message= Finished turn, conversation_id=%s, changes=%s, "
            "warnings=%s, spans=%s",
            conversation_id,
            len(outcome.changes),
            len(outcome.warnings),
            len(outcome.spans),
        )
        return _as_turn_response(replay)

    async def view(self, conversation_id: str, user_id: str) -> ConversationView:
        conversation, messages, versions = (
            await self._repository.load_conversation_view(conversation_id, user_id)
        )
        current = next((v for v in versions if v.is_current), None)

        return ConversationView(
            conversation_id=conversation.id,
            status=conversation.status,
            job_id=conversation.job_id,
            base_resume_id=conversation.base_resume_id,
            language=conversation.language,
            messages=[
                ConversationMessage(
                    seq=m.seq,
                    role=m.role,
                    content=m.content,
                    warnings=m.warnings or [],
                )
                for m in messages
            ],
            current_resume=current.resume if current else conversation.base_resume,
            versions=[
                ConversationVersion(
                    version=v.version, changes=v.changes, is_current=v.is_current
                )
                for v in versions
            ],
            turns_left=max(conversation.max_turns - conversation.turn_count, 0),
            score_before=conversation.score_before,
            score_after=conversation.score_after,
        )

    async def mark_applied(
        self,
        *,
        conversation_id: str,
        user_id: str,
        applied_resume_id: str,
        score_after: int | None,
    ) -> str:
        conversation = await self._repository.mark_applied(
            conversation_id=conversation_id,
            user_id=user_id,
            applied_resume_id=applied_resume_id,
            score_after=score_after,
        )
        log.info(
            "message= Conversation closed as applied, conversation_id=%s, "
            "score_before=%s, score_after=%s",
            conversation_id,
            conversation.score_before,
            conversation.score_after,
        )
        return conversation.status

    async def _run(self, claim: TurnClaim, request_uuid: str) -> TurnOutcome:
        context = TurnContext(
            conversation_id=claim.conversation_id,
            job_snapshot=claim.job_snapshot,
            base_resume=claim.base_resume,
            editor=ResumeEditor(
                claim.base_resume, claim.current_resume, claim.language
            ),
            max_steps=claim.max_steps,
            language=claim.language,
            input_price_per_1m=claim.input_price_per_1m,
            output_price_per_1m=claim.output_price_per_1m,
            job_requirements=claim.job_requirements,
        )
        turns_left = max(claim.max_turns - claim.turn_count - 1, 0)

        return await self._turn_runner(
            context=context,
            handle=self._model_builder(),
            ml_client=self._ml_client,
            request_uuid=request_uuid,
            message=claim.pending_message,
            history=claim.history,
            turns_left=turns_left,
        )


def _as_turn_response(replay: TurnReplay) -> TurnResponse:
    return TurnResponse(
        reply=replay.reply,
        resume=replay.resume,
        changes=replay.changes,
        warnings=replay.warnings,
        version=replay.version,
        status=replay.status,
        turns_left=replay.turns_left,
    )
