from __future__ import annotations

import asyncio
import unittest
from datetime import datetime
from unittest.mock import patch

from sqlalchemy import func, select

from tests.support import (
    TemporaryDatabase,
    new_conversation,
    new_message,
    new_resume_version,
    new_trace_span,
)

from config import Settings, reset_settings
from db.housekeeping import HousekeepingReport, housekeeping_loop, run_housekeeping
from db.models import Conversation, ConversationStatus, Message, ResumeVersion, TraceSpan


def settings_with(**overrides) -> Settings:
    reset_settings()
    settings = Settings()
    for name, value in overrides.items():
        setattr(settings, name, value)
    return settings


class HousekeepingTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        self.database = TemporaryDatabase()
        await self.database.create_schema()

    async def asyncTearDown(self) -> None:
        await self.database.dispose()

    async def _store(self, *rows) -> None:
        async with self.database.session_factory() as session:
            session.add_all(rows)
            await session.commit()

    async def _run(self, **overrides) -> HousekeepingReport:
        return await run_housekeeping(
            session_factory=self.database.session_factory,
            settings=settings_with(**overrides),
        )

    async def _count(self, entity) -> int:
        async with self.database.session_factory() as session:
            return await session.scalar(select(func.count()).select_from(entity))

    async def _one(self, entity):
        async with self.database.session_factory() as session:
            return await session.scalar(select(entity))

    async def test_an_untouched_open_conversation_is_abandoned(self) -> None:
        await self._store(new_conversation(age_days=30))

        report = await self._run(abandon_after_days=7)

        self.assertEqual(report.abandoned, 1)
        self.assertEqual((await self._one(Conversation)).status, ConversationStatus.ABANDONED)

    async def test_a_conversation_still_within_the_window_is_left_alone(self) -> None:
        await self._store(new_conversation(age_days=2))

        report = await self._run(abandon_after_days=7)

        self.assertEqual(report.abandoned, 0)
        self.assertEqual((await self._one(Conversation)).status, ConversationStatus.ACTIVE)

    async def test_a_closed_conversation_is_never_abandoned(self) -> None:
        await self._store(
            new_conversation(age_days=30, status=ConversationStatus.APPLIED)
        )

        report = await self._run(abandon_after_days=7, trace_ttl_days=9000)

        self.assertEqual(report.abandoned, 0)
        self.assertEqual((await self._one(Conversation)).status, ConversationStatus.APPLIED)

    async def test_spans_of_a_closed_conversation_lose_their_payload(self) -> None:
        conversation = new_conversation(age_days=60, status=ConversationStatus.APPLIED)
        await self._store(conversation, new_trace_span(conversation))

        report = await self._run(trace_ttl_days=30)

        span = await self._one(TraceSpan)
        self.assertEqual(report.purged_spans, 1)
        self.assertIsNone(span.input)
        self.assertIsNone(span.output)

    async def test_purging_a_span_keeps_what_the_cost_analytics_need(self) -> None:
        conversation = new_conversation(age_days=60, status=ConversationStatus.EXHAUSTED)
        await self._store(conversation, new_trace_span(conversation))

        await self._run(trace_ttl_days=30)

        span = await self._one(TraceSpan)
        self.assertEqual(span.prompt_tokens, 1200)
        self.assertEqual(span.completion_tokens, 300)
        self.assertEqual(span.latency_ms, 1800)
        self.assertIsNotNone(span.cost_usd)

    async def test_spans_of_an_open_conversation_keep_their_payload(self) -> None:
        conversation = new_conversation(age_days=60, status=ConversationStatus.ACTIVE)
        await self._store(conversation, new_trace_span(conversation))

        await self._run(abandon_after_days=9000, trace_ttl_days=30)

        self.assertIsNotNone((await self._one(TraceSpan)).input)

    async def test_an_expired_conversation_takes_its_children_with_it(self) -> None:
        conversation = new_conversation(age_days=400, status=ConversationStatus.APPLIED)
        await self._store(
            conversation,
            new_message(conversation),
            new_resume_version(conversation),
            new_trace_span(conversation),
        )

        report = await self._run(conversation_ttl_days=180)

        self.assertEqual(report.deleted_conversations, 1)
        self.assertEqual(await self._count(Conversation), 0)
        self.assertEqual(await self._count(Message), 0)
        self.assertEqual(await self._count(ResumeVersion), 0)
        self.assertEqual(await self._count(TraceSpan), 0)

    async def test_a_conversation_younger_than_the_ttl_survives(self) -> None:
        conversation = new_conversation(age_days=10, status=ConversationStatus.APPLIED)
        await self._store(conversation, new_message(conversation))

        report = await self._run(conversation_ttl_days=180)

        self.assertEqual(report.deleted_conversations, 0)
        self.assertEqual(await self._count(Conversation), 1)

    async def test_every_ttl_at_zero_empties_the_database(self) -> None:
        conversation = new_conversation(age_days=0, status=ConversationStatus.ACTIVE)
        await self._store(
            conversation,
            new_message(conversation),
            new_resume_version(conversation),
            new_trace_span(conversation),
        )

        await self._run(
            abandon_after_days=0, trace_ttl_days=0, conversation_ttl_days=0
        )

        self.assertEqual(await self._count(Conversation), 0)
        self.assertEqual(await self._count(Message), 0)
        self.assertEqual(await self._count(ResumeVersion), 0)
        self.assertEqual(await self._count(TraceSpan), 0)

    async def test_abandoning_a_conversation_stamps_the_moment_it_happened(self) -> None:
        await self._store(new_conversation(age_days=30))

        before = datetime.now()
        await self._run(abandon_after_days=7)

        self.assertGreaterEqual((await self._one(Conversation)).updated_on, before)


class HousekeepingLoopTest(unittest.IsolatedAsyncioTestCase):

    async def _run_loop(self, run) -> None:
        with patch("db.housekeeping.run_housekeeping", side_effect=run):
            with patch(
                "db.housekeeping.get_settings",
                return_value=settings_with(housekeeping_interval_seconds=0),
            ):
                task = asyncio.create_task(housekeeping_loop())
                await asyncio.sleep(0.05)
                task.cancel()
                with self.assertRaises(asyncio.CancelledError):
                    await task

    async def test_the_loop_runs_over_and_over(self) -> None:
        passes = []

        async def run() -> HousekeepingReport:
            passes.append(1)
            return HousekeepingReport()

        await self._run_loop(run)

        self.assertGreater(len(passes), 1)

    async def test_a_failed_pass_does_not_kill_the_loop(self) -> None:
        passes = []

        async def run() -> HousekeepingReport:
            passes.append(1)
            if len(passes) == 1:
                raise RuntimeError("the database went away")
            return HousekeepingReport()

        await self._run_loop(run)

        self.assertGreater(len(passes), 1)


if __name__ == "__main__":
    unittest.main()
