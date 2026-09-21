from __future__ import annotations

import asyncio
import logging
from datetime import timedelta

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agent.context import SpanRecord, TurnContext, TurnOutcome, apply_pricing, utc_now
from agent.graph import build_greeting, run_turn
from agent.language import verify_resume_language
from client.lynq_ml_client import SkillExtractionFailed
from config import Settings
from db import repository
from db.models import Conversation, ConversationStatus, Message, MessageRole, SpanKind
from model.conversation import (
    AppliedRequest,
    AppliedResponse,
    ConversationView,
    CreateConversationRequest,
    CreateConversationResponse,
    MessageView,
    TurnRequest,
    TurnResponse,
    VersionView,
)
from model.errors import (
    AgentFailed,
    AlreadyApplied,
    ConversationExhausted,
    ConversationNotFound,
    ErrorCode,
    NotTheOwner,
    TurnInProgress,
)

log = logging.getLogger(__name__)

HISTORY_PAIRS = 4
TIMEOUT_MARGIN_SECONDS = 30


class ConversationService:

    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        lynq_ml_client,
        settings: Settings,
        loop_runner=run_turn,
    ) -> None:
        self._session_factory = session_factory
        self._lynq_ml_client = lynq_ml_client
        self._settings = settings
        self._loop_runner = loop_runner

    async def create(
        self, request: CreateConversationRequest, request_uuid: str, user_id: str
    ) -> CreateConversationResponse:
        job = request.job.model_dump(by_alias=True)
        job["description"] = self._truncate(job.get("description") or "")
        job["extractedSkills"] = await self._extract_skills(job, request_uuid, user_id)

        resume_language = verify_resume_language(
            request.base_resume, request.resume_language
        )
        greeting = build_greeting(job, request.language)
        now = utc_now()

        conversation = Conversation(
            id=repository.new_id(),
            user_id=user_id,
            job_id=job["id"],
            base_resume_id=request.base_resume_id,
            job_snapshot=job,
            base_resume=request.base_resume,
            language=request.language,
            resume_language=resume_language,
            status=ConversationStatus.AWAITING_CONFIRMATION,
            llm_provider=self._settings.llm_provider,
            llm_model=self._settings.llm_model,
            input_price_per_1m=self._settings.input_price_per_1m,
            output_price_per_1m=self._settings.output_price_per_1m,
            max_turns=self._settings.max_turns,
            max_steps=self._settings.max_steps,
            turn_count=0,
            created_on=now,
            updated_on=now,
        )

        async with self._session_factory() as session:
            await repository.create_conversation(session, conversation)
            await repository.append_message(
                session,
                conversation.id,
                MessageRole.ASSISTANT,
                greeting,
                created_on=now,
            )
            await session.commit()

        log.info(
            "message= Conversation created, conversationId=%s, jobId=%s, "
            "extractedSkills=%s",
            conversation.id,
            conversation.job_id,
            len(job["extractedSkills"]),
        )
        return CreateConversationResponse(
            conversation_id=conversation.id,
            greeting=greeting,
            status=conversation.status,
        )

    async def turn(
        self, conversation_id: str, request: TurnRequest, user_id: str
    ) -> TurnResponse:
        claimed = await self._claim(conversation_id, request, user_id)
        if isinstance(claimed, TurnResponse):
            return claimed

        context, run_token, user_message_id = claimed

        try:
            outcome = await asyncio.wait_for(
                self._loop_runner(context), timeout=self._loop_timeout()
            )
        except Exception as exc:
            await self._fail_turn(conversation_id, run_token, user_message_id, context, exc)
            raise AgentFailed(f"The agent could not finish the turn: {exc}") from exc

        return await self._persist(conversation_id, run_token, user_message_id, outcome)

    async def view(self, conversation_id: str, user_id: str) -> ConversationView:
        async with self._session_factory() as session:
            conversation = await self._owned(session, conversation_id, user_id)
            messages = await repository.list_messages(session, conversation_id)
            versions = await repository.list_versions(session, conversation_id)
            current = await repository.current_version(session, conversation_id)

            return ConversationView(
                conversation_id=conversation.id,
                status=conversation.status,
                turn_count=conversation.turn_count,
                turns_left=self._turns_left(conversation),
                messages=[
                    MessageView(
                        seq=message.seq,
                        role=message.role,
                        content=message.content,
                        warnings=message.warnings or [],
                        created_on=message.created_on,
                    )
                    for message in messages
                ],
                current_resume=current.resume if current else conversation.base_resume,
                versions=[
                    VersionView(
                        version=version.version,
                        is_current=version.is_current,
                        changes=version.changes,
                        created_on=version.created_on,
                    )
                    for version in versions
                ],
            )

    async def mark_applied(
        self, conversation_id: str, request: AppliedRequest, user_id: str
    ) -> AppliedResponse:
        async with self._session_factory() as session:
            conversation = await self._owned(
                session, conversation_id, user_id, for_update=True
            )

            if conversation.status == ConversationStatus.APPLIED:
                if conversation.applied_resume_id != request.applied_resume_id:
                    raise AlreadyApplied(
                        f"Conversation {conversation_id} was already applied with "
                        f"resume {conversation.applied_resume_id}"
                    )
                return AppliedResponse(status=conversation.status)

            await repository.mark_applied(
                session, conversation, request.applied_resume_id
            )
            await session.commit()

            log.info(
                "message= Conversation applied, conversationId=%s, appliedResumeId=%s",
                conversation_id,
                request.applied_resume_id,
            )
            return AppliedResponse(status=conversation.status)

    async def _claim(
        self, conversation_id: str, request: TurnRequest, user_id: str
    ) -> TurnResponse | tuple[TurnContext, str, str]:
        async with self._session_factory() as session:
            conversation = await self._owned(
                session, conversation_id, user_id, for_update=True
            )
            self._guard(conversation)

            existing = await repository.find_by_turn_key(
                session, conversation_id, request.turn_key
            )
            if existing is not None:
                replay = await self._replay(session, conversation, existing)
                if replay is not None:
                    log.info(
                        "message= Replaying a known turn, conversationId=%s, turnKey=%s",
                        conversation_id,
                        request.turn_key,
                    )
                    return replay
                log.warning(
                    "message= Reusing the orphan user message of a dead turn, "
                    "conversationId=%s, turnKey=%s",
                    conversation_id,
                    request.turn_key,
                )

            run_token = await repository.claim_turn(session, conversation)
            user_message = existing or await repository.append_message(
                session,
                conversation_id,
                MessageRole.USER,
                request.message,
                turn_key=request.turn_key,
            )
            context = await self._context(session, conversation, request, run_token)
            await session.commit()

            return context, run_token, user_message.id

    async def _persist(
        self,
        conversation_id: str,
        run_token: str,
        user_message_id: str,
        outcome: TurnOutcome,
    ) -> TurnResponse:
        async with self._session_factory() as session:
            conversation = await repository.load_for_turn(session, conversation_id)

            if conversation is None or conversation.run_token != run_token:
                await self._discard_zombie(session, conversation_id, user_message_id)
                raise AgentFailed(
                    "Another turn took over this conversation while the agent was "
                    "running; nothing was persisted",
                    ErrorCode.STALE_RUN,
                )

            apply_pricing(
                outcome.spans,
                conversation.input_price_per_1m,
                conversation.output_price_per_1m,
            )
            assistant = await repository.append_message(
                session,
                conversation_id,
                MessageRole.ASSISTANT,
                outcome.reply,
                warnings=outcome.warnings or None,
            )
            version = await repository.save_version(
                session,
                conversation_id,
                outcome.resume,
                outcome.changes,
                produced_by=assistant.id,
            )
            await repository.save_spans(
                session, conversation_id, user_message_id, outcome.spans
            )
            await repository.close_turn(session, conversation, outcome.spans)
            await session.commit()

            log.info(
                "message= Turn persisted, conversationId=%s, version=%s, status=%s",
                conversation_id,
                version.version,
                conversation.status,
            )
            return TurnResponse(
                reply=outcome.reply,
                resume=outcome.resume,
                changes=outcome.changes,
                warnings=outcome.warnings,
                version=version.version,
                status=conversation.status,
                turns_left=self._turns_left(conversation),
            )

    async def _discard_zombie(
        self, session: AsyncSession, conversation_id: str, user_message_id: str
    ) -> None:
        await self._drop_orphan(session, user_message_id)
        await repository.save_spans(
            session,
            conversation_id,
            None,
            [
                SpanRecord(
                    step=0,
                    kind=SpanKind.ERROR,
                    name="stale_run",
                    error="the run token no longer matches: this turn was superseded",
                )
            ],
        )
        await session.commit()
        log.warning(
            "message= Discarded a superseded turn, conversationId=%s", conversation_id
        )

    async def _fail_turn(
        self,
        conversation_id: str,
        run_token: str,
        user_message_id: str,
        context: TurnContext,
        error: Exception,
    ) -> None:
        log.error(
            "message= Turn failed, conversationId=%s", conversation_id, exc_info=error
        )
        async with self._session_factory() as session:
            conversation = await repository.load_for_turn(session, conversation_id)
            if conversation is None or conversation.run_token != run_token:
                log.warning(
                    "message= The failed turn was already superseded, conversationId=%s",
                    conversation_id,
                )
                return

            await self._drop_orphan(session, user_message_id)
            partial = list(context.spans)
            partial.append(
                SpanRecord(
                    step=len(partial) + 1,
                    kind=SpanKind.ERROR,
                    name=type(error).__name__,
                    error=str(error),
                )
            )
            await repository.save_spans(session, conversation_id, None, partial)
            await repository.release_turn(session, conversation)
            await session.commit()

    async def _drop_orphan(self, session: AsyncSession, user_message_id: str) -> None:
        orphan = await session.get(Message, user_message_id)
        if orphan is not None:
            await repository.delete_message(session, orphan)

    async def _context(
        self,
        session: AsyncSession,
        conversation: Conversation,
        request: TurnRequest,
        run_token: str,
    ) -> TurnContext:
        current = await repository.current_version(session, conversation.id)
        history = await repository.recent_messages(
            session, conversation.id, HISTORY_PAIRS * 2
        )
        return TurnContext(
            conversation_id=conversation.id,
            run_token=run_token,
            language=conversation.language,
            resume_language=conversation.resume_language,
            job_snapshot=conversation.job_snapshot,
            base_resume=conversation.base_resume,
            current_resume=current.resume if current else conversation.base_resume,
            history=[(message.role, message.content) for message in history],
            message=request.message,
            max_steps=conversation.max_steps,
            turns_left=self._turns_left(conversation),
            resume_version_id=current.id if current else None,
        )

    async def _replay(
        self, session: AsyncSession, conversation: Conversation, existing: Message
    ) -> TurnResponse | None:
        reply = await repository.find_reply_after(
            session, conversation.id, existing.seq
        )
        if reply is None:
            return None

        version = await repository.version_produced_by(session, reply.id)
        current = version or await repository.current_version(session, conversation.id)
        return TurnResponse(
            reply=reply.content,
            resume=current.resume if current else conversation.base_resume,
            changes=version.changes if version else [],
            warnings=reply.warnings or [],
            version=version.version if version else 0,
            status=conversation.status,
            turns_left=self._turns_left(conversation),
        )

    async def _owned(
        self,
        session: AsyncSession,
        conversation_id: str,
        user_id: str,
        for_update: bool = False,
    ) -> Conversation:
        conversation = await (
            repository.load_for_turn(session, conversation_id)
            if for_update
            else repository.load(session, conversation_id)
        )
        if conversation is None:
            raise ConversationNotFound(conversation_id)
        if conversation.user_id != user_id:
            raise NotTheOwner(conversation_id)
        return conversation

    def _guard(self, conversation: Conversation) -> None:
        if conversation.status == ConversationStatus.RUNNING:
            deadline = utc_now() - timedelta(
                seconds=self._settings.turn_timeout_seconds
            )
            if conversation.updated_on > deadline:
                raise TurnInProgress()
            log.warning(
                "message= Taking over a turn left RUNNING since %s, conversationId=%s",
                conversation.updated_on,
                conversation.id,
            )
        elif conversation.status == ConversationStatus.APPLIED:
            raise AlreadyApplied(
                f"Conversation {conversation.id} was already applied and is closed"
            )
        elif conversation.status in ConversationStatus.CLOSED:
            raise ConversationExhausted(
                f"Conversation {conversation.id} is {conversation.status}"
            )

        if conversation.turn_count >= conversation.max_turns:
            raise ConversationExhausted(
                f"Conversation {conversation.id} used its {conversation.max_turns} turns"
            )

    async def _extract_skills(
        self, job: dict, request_uuid: str, user_id: str
    ) -> list[str]:
        try:
            return await self._lynq_ml_client.extract_skills(
                job, request_uuid, user_id
            )
        except SkillExtractionFailed as exc:
            log.warning(
                "message= Skill extraction failed, falling back to the skills the "
                "posting already declares, jobId=%s, %s",
                job.get("id"),
                exc,
            )
            return list(job.get("skills") or [])

    def _truncate(self, description: str) -> str:
        limit = self._settings.job_description_max_chars
        return description if len(description) <= limit else description[:limit]

    def _loop_timeout(self) -> int:
        return max(self._settings.turn_timeout_seconds - TIMEOUT_MARGIN_SECONDS, 1)

    @staticmethod
    def _turns_left(conversation: Conversation) -> int:
        return max(conversation.max_turns - conversation.turn_count, 0)
