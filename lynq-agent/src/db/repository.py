from __future__ import annotations

import uuid
from datetime import datetime
from decimal import Decimal

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from agent.context import SpanRecord, utc_now
from db.models import (
    Conversation,
    ConversationStatus,
    Message,
    MessageRole,
    ResumeVersion,
    SpanKind,
    TraceSpan,
)


def new_id() -> str:
    return str(uuid.uuid4())


async def create_conversation(
    session: AsyncSession, conversation: Conversation
) -> Conversation:
    session.add(conversation)
    await session.flush()
    return conversation


async def load(session: AsyncSession, conversation_id: str) -> Conversation | None:
    return await session.scalar(
        select(Conversation).where(Conversation.id == conversation_id)
    )


async def load_for_turn(
    session: AsyncSession, conversation_id: str
) -> Conversation | None:
    return await session.scalar(
        select(Conversation)
        .where(Conversation.id == conversation_id)
        .with_for_update()
    )


async def find_by_turn_key(
    session: AsyncSession, conversation_id: str, turn_key: str
) -> Message | None:
    return await session.scalar(
        select(Message).where(
            Message.conversation_id == conversation_id, Message.turn_key == turn_key
        )
    )


async def find_reply_after(
    session: AsyncSession, conversation_id: str, seq: int
) -> Message | None:
    return await session.scalar(
        select(Message)
        .where(
            Message.conversation_id == conversation_id,
            Message.role == MessageRole.ASSISTANT,
            Message.seq > seq,
        )
        .order_by(Message.seq)
        .limit(1)
    )


async def next_seq(session: AsyncSession, conversation_id: str) -> int:
    highest = await session.scalar(
        select(func.max(Message.seq)).where(Message.conversation_id == conversation_id)
    )
    return (highest or 0) + 1


async def append_message(
    session: AsyncSession,
    conversation_id: str,
    role: str,
    content: str,
    turn_key: str | None = None,
    warnings: list[str] | None = None,
    created_on: datetime | None = None,
) -> Message:
    message = Message(
        id=new_id(),
        conversation_id=conversation_id,
        seq=await next_seq(session, conversation_id),
        role=role,
        content=content,
        turn_key=turn_key,
        warnings=warnings,
        created_on=created_on or utc_now(),
    )
    session.add(message)
    await session.flush()
    return message


async def delete_message(session: AsyncSession, message: Message) -> None:
    await session.delete(message)
    await session.flush()


async def list_messages(session: AsyncSession, conversation_id: str) -> list[Message]:
    result = await session.scalars(
        select(Message)
        .where(Message.conversation_id == conversation_id)
        .order_by(Message.seq)
    )
    return list(result)


async def recent_messages(
    session: AsyncSession, conversation_id: str, limit: int
) -> list[Message]:
    result = await session.scalars(
        select(Message)
        .where(Message.conversation_id == conversation_id)
        .order_by(Message.seq.desc())
        .limit(limit)
    )
    return sorted(result, key=lambda message: message.seq)


async def current_version(
    session: AsyncSession, conversation_id: str
) -> ResumeVersion | None:
    return await session.scalar(
        select(ResumeVersion).where(
            ResumeVersion.conversation_id == conversation_id,
            ResumeVersion.is_current.is_(True),
        )
    )


async def version_produced_by(
    session: AsyncSession, message_id: str
) -> ResumeVersion | None:
    return await session.scalar(
        select(ResumeVersion).where(ResumeVersion.produced_by == message_id)
    )


async def list_versions(
    session: AsyncSession, conversation_id: str
) -> list[ResumeVersion]:
    result = await session.scalars(
        select(ResumeVersion)
        .where(ResumeVersion.conversation_id == conversation_id)
        .order_by(ResumeVersion.version)
    )
    return list(result)


async def save_version(
    session: AsyncSession,
    conversation_id: str,
    resume: dict,
    changes: list,
    produced_by: str | None,
    created_on: datetime | None = None,
) -> ResumeVersion:
    await session.execute(
        update(ResumeVersion)
        .where(
            ResumeVersion.conversation_id == conversation_id,
            ResumeVersion.is_current.is_(True),
        )
        .values(is_current=False)
        .execution_options(synchronize_session="fetch")
    )
    highest = await session.scalar(
        select(func.max(ResumeVersion.version)).where(
            ResumeVersion.conversation_id == conversation_id
        )
    )
    version = ResumeVersion(
        id=new_id(),
        conversation_id=conversation_id,
        version=(highest or 0) + 1,
        resume=resume,
        changes=changes,
        produced_by=produced_by,
        is_current=True,
        created_on=created_on or utc_now(),
    )
    session.add(version)
    await session.flush()
    return version


async def save_spans(
    session: AsyncSession,
    conversation_id: str,
    message_id: str | None,
    records: list[SpanRecord],
) -> list[TraceSpan]:
    spans = [
        TraceSpan(
            id=new_id(),
            conversation_id=conversation_id,
            message_id=message_id,
            parent_id=record.parent_id,
            step=record.step,
            kind=record.kind,
            name=record.name,
            input=record.input,
            output=record.output,
            prompt_tokens=record.prompt_tokens,
            completion_tokens=record.completion_tokens,
            cached_prompt_tokens=record.cached_prompt_tokens,
            cost_usd=record.cost_usd,
            latency_ms=record.latency_ms,
            error=record.error,
            created_on=record.created_on,
        )
        for record in records
    ]
    session.add_all(spans)
    await session.flush()
    return spans


async def claim_turn(session: AsyncSession, conversation: Conversation) -> str:
    conversation.status = ConversationStatus.RUNNING
    conversation.run_token = new_id()
    conversation.updated_on = utc_now()
    await session.flush()
    return conversation.run_token


async def release_turn(session: AsyncSession, conversation: Conversation) -> None:
    conversation.status = ConversationStatus.ACTIVE
    conversation.run_token = None
    conversation.updated_on = utc_now()
    await session.flush()


async def close_turn(
    session: AsyncSession, conversation: Conversation, records: list[SpanRecord]
) -> None:
    conversation.turn_count += 1
    conversation.status = (
        ConversationStatus.EXHAUSTED
        if conversation.turn_count >= conversation.max_turns
        else ConversationStatus.ACTIVE
    )
    conversation.run_token = None
    conversation.llm_calls += sum(1 for r in records if r.kind == SpanKind.LLM)
    conversation.total_prompt_tokens += sum(r.prompt_tokens or 0 for r in records)
    conversation.total_completion_tokens += sum(
        r.completion_tokens or 0 for r in records
    )
    conversation.cost_usd = Decimal(conversation.cost_usd or 0) + sum(
        (r.cost_usd or Decimal("0") for r in records), Decimal("0")
    )
    conversation.updated_on = utc_now()
    await session.flush()


async def mark_applied(
    session: AsyncSession, conversation: Conversation, applied_resume_id: str
) -> None:
    conversation.status = ConversationStatus.APPLIED
    conversation.applied_resume_id = applied_resume_id
    conversation.run_token = None
    conversation.updated_on = utc_now()
    await session.flush()
