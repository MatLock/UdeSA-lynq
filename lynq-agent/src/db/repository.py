from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Any

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from db.errors import (
    ConversationExhausted,
    ConversationNotFound,
    ConversationNotOwned,
    TurnAlreadyRunning,
)
from db.models import (
    Conversation,
    ConversationStatus,
    Message,
    MessageRole,
    ResumeVersion,
    SpanKind,
    TraceSpan,
)


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def new_id() -> str:
    return str(uuid.uuid4())


@dataclass
class TurnClaim:
    conversation_id: str
    user_message_id: str
    pending_message: str
    seq: int
    job_snapshot: dict
    base_resume: dict
    current_resume: dict
    current_version: int
    language: str
    max_steps: int
    max_turns: int
    turn_count: int
    llm_provider: str
    llm_model: str
    input_price_per_1m: Decimal
    output_price_per_1m: Decimal
    job_requirements: list[str] | None
    history: list[dict] = field(default_factory=list)


@dataclass
class TurnReplay:
    reply: str
    resume: dict
    changes: list
    warnings: list
    version: int
    status: str
    turns_left: int


@dataclass
class SpanRecord:
    step: int
    kind: str
    name: str
    input: str | None = None
    output: str | None = None
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    cached_prompt_tokens: int | None = None
    cost_usd: Decimal | None = None
    latency_ms: int | None = None
    error: str | None = None
    created_on: datetime | None = None
    parent_id: str | None = None


class ConversationRepository:

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._session_factory = session_factory

    async def create_conversation(
        self,
        *,
        user_id: str,
        job_id: str,
        job_snapshot: dict,
        base_resume_id: str,
        base_resume: dict,
        language: str,
        status: str,
        llm_provider: str,
        llm_model: str,
        input_price_per_1m: Decimal,
        output_price_per_1m: Decimal,
        max_turns: int,
        max_steps: int,
        score_before: int | None,
        greeting: str,
    ) -> Conversation:
        created = now()
        conversation = Conversation(
            id=new_id(),
            user_id=user_id,
            job_id=job_id,
            base_resume_id=base_resume_id,
            job_snapshot=job_snapshot,
            base_resume=base_resume,
            language=language,
            status=status,
            llm_provider=llm_provider,
            llm_model=llm_model,
            input_price_per_1m=input_price_per_1m,
            output_price_per_1m=output_price_per_1m,
            max_turns=max_turns,
            max_steps=max_steps,
            turn_count=0,
            score_before=score_before,
            created_on=created,
            updated_on=created,
        )

        async with self._session_factory() as session:
            async with session.begin():
                session.add(conversation)
                await session.flush()
                await session.refresh(conversation, ["short_id"])
                session.add(
                    ResumeVersion(
                        id=new_id(),
                        conversation_id=conversation.id,
                        version=0,
                        resume=base_resume,
                        changes=[],
                        produced_by=None,
                        is_current=True,
                        created_on=created,
                    )
                )
                session.add(
                    Message(
                        id=new_id(),
                        conversation_id=conversation.id,
                        seq=0,
                        role=MessageRole.ASSISTANT,
                        content=greeting,
                        turn_key=None,
                        warnings=[],
                        created_on=created,
                    )
                )
        return conversation

    async def get_conversation(self, conversation_id: str) -> Conversation | None:
        async with self._session_factory() as session:
            return await session.get(Conversation, conversation_id)

    async def load_conversation_view(
        self, conversation_id: str, user_id: str
    ) -> tuple[Conversation, list[Message], list[ResumeVersion]]:
        async with self._session_factory() as session:
            conversation = await session.get(Conversation, conversation_id)
            if conversation is None:
                raise ConversationNotFound(conversation_id)
            if conversation.user_id != user_id:
                raise ConversationNotOwned(conversation_id)

            messages = (
                await session.scalars(
                    select(Message)
                    .where(Message.conversation_id == conversation_id)
                    .order_by(Message.seq)
                )
            ).all()
            versions = (
                await session.scalars(
                    select(ResumeVersion)
                    .where(ResumeVersion.conversation_id == conversation_id)
                    .order_by(ResumeVersion.version)
                )
            ).all()
            return conversation, list(messages), list(versions)

    async def claim_turn(
        self,
        *,
        conversation_id: str,
        user_id: str,
        message: str,
        turn_key: str,
        turn_timeout_seconds: int,
        history_pairs: int,
    ) -> TurnClaim | TurnReplay:
        async with self._session_factory() as session:
            async with session.begin():
                conversation = await session.scalar(
                    select(Conversation)
                    .where(Conversation.id == conversation_id)
                    .with_for_update()
                )
                if conversation is None:
                    raise ConversationNotFound(conversation_id)
                if conversation.user_id != user_id:
                    raise ConversationNotOwned(conversation_id)

                replay = await self._replay_of(session, conversation, turn_key)
                if replay is not None:
                    return replay

                self._guard_turn_is_startable(conversation, turn_timeout_seconds)

                seq = await self._next_seq(session, conversation_id)
                user_message = Message(
                    id=new_id(),
                    conversation_id=conversation_id,
                    seq=seq,
                    role=MessageRole.USER,
                    content=message,
                    turn_key=turn_key,
                    warnings=None,
                    created_on=now(),
                )
                session.add(user_message)

                conversation.status = ConversationStatus.RUNNING
                conversation.updated_on = now()

                current = await self._current_version(session, conversation_id)
                history = await self._history(
                    session, conversation_id, seq, history_pairs
                )

                return TurnClaim(
                    conversation_id=conversation_id,
                    user_message_id=user_message.id,
                    pending_message=message,
                    seq=seq,
                    job_snapshot=conversation.job_snapshot,
                    base_resume=conversation.base_resume,
                    current_resume=current.resume,
                    current_version=current.version,
                    language=conversation.language,
                    max_steps=conversation.max_steps,
                    max_turns=conversation.max_turns,
                    turn_count=conversation.turn_count,
                    llm_provider=conversation.llm_provider,
                    llm_model=conversation.llm_model,
                    input_price_per_1m=conversation.input_price_per_1m,
                    output_price_per_1m=conversation.output_price_per_1m,
                    job_requirements=conversation.job_requirements,
                    history=history,
                )

    async def finish_turn(
        self,
        *,
        claim: TurnClaim,
        reply: str,
        warnings: list[str],
        resume: dict | None,
        changes: list[dict],
        spans: list[SpanRecord],
        job_requirements: list[str] | None,
    ) -> TurnReplay:
        async with self._session_factory() as session:
            async with session.begin():
                conversation = await session.scalar(
                    select(Conversation)
                    .where(Conversation.id == claim.conversation_id)
                    .with_for_update()
                )
                if conversation is None:
                    raise ConversationNotFound(claim.conversation_id)

                seq = await self._next_seq(session, claim.conversation_id)
                assistant_message = Message(
                    id=new_id(),
                    conversation_id=claim.conversation_id,
                    seq=seq,
                    role=MessageRole.ASSISTANT,
                    content=reply,
                    turn_key=None,
                    warnings=warnings,
                    created_on=now(),
                )
                session.add(assistant_message)

                version = claim.current_version
                stored_resume = claim.current_resume
                stored_changes: list = []
                if resume is not None and changes:
                    current = await self._current_version(
                        session, claim.conversation_id
                    )
                    current.is_current = False
                    version = current.version + 1
                    stored_resume = resume
                    stored_changes = changes
                    session.add(
                        ResumeVersion(
                            id=new_id(),
                            conversation_id=claim.conversation_id,
                            version=version,
                            resume=resume,
                            changes=changes,
                            produced_by=assistant_message.id,
                            is_current=True,
                            created_on=now(),
                        )
                    )

                self._add_spans(
                    session, claim.conversation_id, assistant_message.id, spans
                )

                conversation.turn_count += 1
                conversation.llm_calls += sum(
                    1 for span in spans if span.kind == SpanKind.LLM
                )
                conversation.total_prompt_tokens += sum(
                    span.prompt_tokens or 0 for span in spans
                )
                conversation.total_completion_tokens += sum(
                    span.completion_tokens or 0 for span in spans
                )
                conversation.cost_usd = conversation.cost_usd + sum(
                    (span.cost_usd or Decimal("0") for span in spans), Decimal("0")
                )
                if job_requirements is not None:
                    conversation.job_requirements = job_requirements
                conversation.status = (
                    ConversationStatus.EXHAUSTED
                    if conversation.turn_count >= conversation.max_turns
                    else ConversationStatus.ACTIVE
                )
                conversation.updated_on = now()

                return TurnReplay(
                    reply=reply,
                    resume=stored_resume,
                    changes=stored_changes,
                    warnings=warnings,
                    version=version,
                    status=conversation.status,
                    turns_left=max(
                        conversation.max_turns - conversation.turn_count, 0
                    ),
                )

    async def fail_turn(self, *, claim: TurnClaim, error: str) -> None:
        async with self._session_factory() as session:
            async with session.begin():
                conversation = await session.scalar(
                    select(Conversation)
                    .where(Conversation.id == claim.conversation_id)
                    .with_for_update()
                )
                user_message = await session.get(Message, claim.user_message_id)
                if user_message is not None:
                    await session.delete(user_message)

                session.add(
                    TraceSpan(
                        id=new_id(),
                        conversation_id=claim.conversation_id,
                        message_id=None,
                        parent_id=None,
                        step=0,
                        kind=SpanKind.ERROR,
                        name="turn",
                        input=None,
                        output=None,
                        error=error,
                        created_on=now(),
                    )
                )

                if conversation is not None:
                    conversation.status = ConversationStatus.ACTIVE
                    conversation.updated_on = now()

    async def mark_applied(
        self,
        *,
        conversation_id: str,
        user_id: str,
        applied_resume_id: str,
        score_after: int | None,
    ) -> Conversation:
        async with self._session_factory() as session:
            async with session.begin():
                conversation = await session.scalar(
                    select(Conversation)
                    .where(Conversation.id == conversation_id)
                    .with_for_update()
                )
                if conversation is None:
                    raise ConversationNotFound(conversation_id)
                if conversation.user_id != user_id:
                    raise ConversationNotOwned(conversation_id)

                already_applied = (
                    conversation.status == ConversationStatus.APPLIED
                    and conversation.applied_resume_id == applied_resume_id
                )
                if already_applied:
                    return conversation

                conversation.status = ConversationStatus.APPLIED
                conversation.applied_resume_id = applied_resume_id
                conversation.score_after = score_after
                conversation.updated_on = now()
                return conversation

    async def current_resume(self, conversation_id: str) -> dict | None:
        async with self._session_factory() as session:
            version = await self._current_version(session, conversation_id)
            return None if version is None else version.resume

    def _guard_turn_is_startable(
        self, conversation: Conversation, turn_timeout_seconds: int
    ) -> None:
        if conversation.status == ConversationStatus.RUNNING:
            deadline = conversation.updated_on + timedelta(
                seconds=turn_timeout_seconds
            )
            if now() < deadline:
                raise TurnAlreadyRunning(conversation.id)

        exhausted = (
            conversation.status == ConversationStatus.EXHAUSTED
            or conversation.turn_count >= conversation.max_turns
        )
        if exhausted:
            conversation.status = ConversationStatus.EXHAUSTED
            conversation.updated_on = now()
            raise ConversationExhausted(conversation.id)

    async def _replay_of(
        self, session: AsyncSession, conversation: Conversation, turn_key: str
    ) -> TurnReplay | None:
        previous = await session.scalar(
            select(Message).where(
                Message.conversation_id == conversation.id,
                Message.turn_key == turn_key,
            )
        )
        if previous is None:
            return None

        answer = await session.scalar(
            select(Message)
            .where(
                Message.conversation_id == conversation.id,
                Message.seq > previous.seq,
                Message.role == MessageRole.ASSISTANT,
            )
            .order_by(Message.seq)
            .limit(1)
        )
        if answer is None:
            raise TurnAlreadyRunning(conversation.id)

        produced = await session.scalar(
            select(ResumeVersion).where(ResumeVersion.produced_by == answer.id)
        )
        if produced is None:
            produced = await session.scalar(
                select(ResumeVersion)
                .where(
                    ResumeVersion.conversation_id == conversation.id,
                    ResumeVersion.created_on <= answer.created_on,
                )
                .order_by(ResumeVersion.version.desc())
                .limit(1)
            )

        return TurnReplay(
            reply=answer.content,
            resume=produced.resume if produced else conversation.base_resume,
            changes=produced.changes if produced and produced.produced_by else [],
            warnings=answer.warnings or [],
            version=produced.version if produced else 0,
            status=conversation.status,
            turns_left=max(conversation.max_turns - conversation.turn_count, 0),
        )

    async def _next_seq(self, session: AsyncSession, conversation_id: str) -> int:
        highest = await session.scalar(
            select(func.max(Message.seq)).where(
                Message.conversation_id == conversation_id
            )
        )
        return 0 if highest is None else highest + 1

    async def _current_version(
        self, session: AsyncSession, conversation_id: str
    ) -> ResumeVersion:
        return await session.scalar(
            select(ResumeVersion).where(
                ResumeVersion.conversation_id == conversation_id,
                ResumeVersion.is_current.is_(True),
            )
        )

    async def _history(
        self,
        session: AsyncSession,
        conversation_id: str,
        current_seq: int,
        history_pairs: int,
    ) -> list[dict]:
        messages = (
            await session.scalars(
                select(Message)
                .where(
                    Message.conversation_id == conversation_id,
                    Message.seq < current_seq,
                )
                .order_by(Message.seq.desc())
                .limit(history_pairs * 2)
            )
        ).all()
        return [
            {"role": message.role, "content": message.content}
            for message in reversed(messages)
        ]

    def _add_spans(
        self,
        session: AsyncSession,
        conversation_id: str,
        message_id: str,
        spans: list[SpanRecord],
    ) -> None:
        for span in spans:
            session.add(
                TraceSpan(
                    id=new_id(),
                    conversation_id=conversation_id,
                    message_id=message_id,
                    parent_id=span.parent_id,
                    step=span.step,
                    kind=span.kind,
                    name=span.name,
                    input=span.input,
                    output=span.output,
                    prompt_tokens=span.prompt_tokens,
                    completion_tokens=span.completion_tokens,
                    cached_prompt_tokens=span.cached_prompt_tokens,
                    cost_usd=span.cost_usd,
                    latency_ms=span.latency_ms,
                    error=span.error,
                    created_on=span.created_on or now(),
                )
            )


def resume_skill_vocabulary(resume: dict[str, Any]) -> list[str]:
    skills = resume.get("skills") or {}
    technical = skills.get("technical") or []
    tools = skills.get("tools") or []
    return [str(name) for name in [*technical, *tools]]
