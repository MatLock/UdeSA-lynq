from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from sqlalchemy import delete, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from config import Settings, get_settings
from db.models import (
    Conversation,
    ConversationStatus,
    Message,
    ResumeVersion,
    TraceSpan,
)
from db.session import get_session_factory

log = logging.getLogger(__name__)

_NO_SYNC = {"synchronize_session": False}


@dataclass
class HousekeepingReport:
    abandoned: int = 0
    purged_spans: int = 0
    deleted_conversations: int = 0


def _now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


async def _mark_abandoned(session: AsyncSession, cutoff: datetime, now: datetime) -> int:
    statement = (
        update(Conversation)
        .where(
            Conversation.status.in_(ConversationStatus.OPEN),
            Conversation.updated_on < cutoff,
        )
        .values(status=ConversationStatus.ABANDONED, updated_on=now)
        .execution_options(**_NO_SYNC)
    )
    return (await session.execute(statement)).rowcount


async def _purge_span_payloads(session: AsyncSession, cutoff: datetime) -> int:
    closed = select(Conversation.id).where(
        Conversation.status.in_(ConversationStatus.CLOSED),
        Conversation.updated_on < cutoff,
    )
    statement = (
        update(TraceSpan)
        .where(
            TraceSpan.conversation_id.in_(closed),
            or_(TraceSpan.input.is_not(None), TraceSpan.output.is_not(None)),
        )
        .values(input=None, output=None)
        .execution_options(**_NO_SYNC)
    )
    return (await session.execute(statement)).rowcount


async def _delete_expired(session: AsyncSession, cutoff: datetime) -> int:
    expired = select(Conversation.id).where(Conversation.created_on < cutoff)

    for child in (TraceSpan, ResumeVersion, Message):
        await session.execute(
            delete(child)
            .where(child.conversation_id.in_(expired))
            .execution_options(**_NO_SYNC)
        )

    statement = (
        delete(Conversation)
        .where(Conversation.created_on < cutoff)
        .execution_options(**_NO_SYNC)
    )
    return (await session.execute(statement)).rowcount


async def run_housekeeping(
    session_factory: async_sessionmaker[AsyncSession] | None = None,
    settings: Settings | None = None,
) -> HousekeepingReport:
    settings = settings or get_settings()
    session_factory = session_factory or get_session_factory()
    now = _now()

    async with session_factory() as session:
        report = HousekeepingReport(
            abandoned=await _mark_abandoned(
                session, now - timedelta(days=settings.abandon_after_days), now
            ),
            purged_spans=await _purge_span_payloads(
                session, now - timedelta(days=settings.trace_ttl_days)
            ),
            deleted_conversations=await _delete_expired(
                session, now - timedelta(days=settings.conversation_ttl_days)
            ),
        )
        await session.commit()

    log.info(
        "message= Housekeeping done. abandoned=%s purgedSpans=%s deletedConversations=%s",
        report.abandoned,
        report.purged_spans,
        report.deleted_conversations,
    )
    return report


async def housekeeping_loop() -> None:
    interval = get_settings().housekeeping_interval_seconds
    while True:
        try:
            await run_housekeeping()
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            log.error("message= Housekeeping failed", exc_info=exc)
        await asyncio.sleep(interval)
