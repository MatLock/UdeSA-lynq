from __future__ import annotations

import unittest
from datetime import timedelta
from decimal import Decimal

from sqlalchemy import select

from tests.support import JOB, TemporaryDatabase, base_resume

from db.errors import (
    ConversationExhausted,
    ConversationNotFound,
    ConversationNotOwned,
    TurnAlreadyRunning,
)
from db.models import Conversation, ConversationStatus, Message, ResumeVersion, SpanKind
from db.repository import ConversationRepository, SpanRecord, TurnClaim, TurnReplay

USER = "user-1"
OTHER_USER = "user-2"


class ConversationRepositoryTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self.database = TemporaryDatabase()
        await self.database.create_schema()
        self.repository = ConversationRepository(self.database.session_factory)

    async def asyncTearDown(self):
        await self.database.dispose()

    async def test_creating_a_conversation_seeds_the_base_version_and_greeting(self):
        conversation = await self._create()

        async with self.database.session_factory() as session:
            versions = (
                await session.scalars(
                    select(ResumeVersion).where(
                        ResumeVersion.conversation_id == conversation.id
                    )
                )
            ).all()
            messages = (
                await session.scalars(
                    select(Message).where(Message.conversation_id == conversation.id)
                )
            ).all()

        self.assertEqual(len(versions), 1)
        self.assertEqual(versions[0].version, 0)
        self.assertTrue(versions[0].is_current)
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0].role, "assistant")
        self.assertEqual(conversation.score_before, 62)

    async def test_claiming_a_turn_commits_the_running_flag_before_the_llm_runs(self):
        conversation = await self._create()

        claim = await self._claim(conversation.id)

        self.assertIsInstance(claim, TurnClaim)
        async with self.database.session_factory() as session:
            stored = await session.get(Conversation, conversation.id)
            self.assertEqual(stored.status, ConversationStatus.RUNNING)

    async def test_a_second_turn_while_one_runs_is_rejected_instead_of_waiting(self):
        conversation = await self._create()
        await self._claim(conversation.id, turn_key="turn-1")

        with self.assertRaises(TurnAlreadyRunning):
            await self._claim(conversation.id, turn_key="turn-2")

    async def test_a_running_turn_older_than_the_timeout_is_rescued(self):
        conversation = await self._create()
        await self._claim(conversation.id, turn_key="turn-1")

        async with self.database.session_factory() as session:
            async with session.begin():
                stored = await session.get(Conversation, conversation.id)
                stored.updated_on = stored.updated_on - timedelta(seconds=1200)

        claim = await self.repository.claim_turn(
            conversation_id=conversation.id,
            user_id=USER,
            message="Segundo intento",
            turn_key="turn-2",
            turn_timeout_seconds=600,
            history_pairs=4,
        )

        self.assertIsInstance(claim, TurnClaim)

    async def test_the_same_turn_key_replays_the_previous_answer(self):
        conversation = await self._create()
        claim = await self._claim(conversation.id, turn_key="turn-1")
        await self._finish(
            claim,
            resume={"summary": "nuevo"},
            changes=[{"section": "summary", "kind": "rewrite", "detail": "x"}],
        )

        replay = await self._claim(conversation.id, turn_key="turn-1")

        self.assertIsInstance(replay, TurnReplay)
        self.assertEqual(replay.reply, "Listo")
        self.assertEqual(replay.version, 1)
        self.assertEqual(replay.resume, {"summary": "nuevo"})

    async def test_a_turn_key_whose_turn_is_still_in_flight_is_a_conflict(self):
        conversation = await self._create()
        await self._claim(conversation.id, turn_key="turn-1")

        with self.assertRaises(TurnAlreadyRunning):
            await self._claim(conversation.id, turn_key="turn-1")

    async def test_finishing_a_turn_rolls_up_tokens_and_cost(self):
        conversation = await self._create()
        claim = await self._claim(conversation.id)
        spans = [
            SpanRecord(
                step=1,
                kind=SpanKind.LLM,
                name="llm",
                prompt_tokens=1000,
                completion_tokens=200,
                cost_usd=Decimal("0.00144"),
            ),
            SpanRecord(step=2, kind=SpanKind.TOOL, name="apply_edit", output="OK"),
        ]

        await self._finish(claim, spans=spans)

        async with self.database.session_factory() as session:
            stored = await session.get(Conversation, conversation.id)

        self.assertEqual(stored.status, ConversationStatus.ACTIVE)
        self.assertEqual(stored.turn_count, 1)
        self.assertEqual(stored.llm_calls, 1)
        self.assertEqual(stored.total_prompt_tokens, 1000)
        self.assertEqual(stored.total_completion_tokens, 200)
        self.assertEqual(stored.job_requirements, ["Kubernetes"])

    async def test_a_turn_that_edits_the_resume_creates_the_next_version(self):
        conversation = await self._create()
        claim = await self._claim(conversation.id)

        replay = await self._finish(
            claim,
            resume={"summary": "adaptado"},
            changes=[{"section": "summary", "kind": "rewrite", "detail": "x"}],
        )

        self.assertEqual(replay.version, 1)
        async with self.database.session_factory() as session:
            versions = (
                await session.scalars(
                    select(ResumeVersion)
                    .where(ResumeVersion.conversation_id == conversation.id)
                    .order_by(ResumeVersion.version)
                )
            ).all()
        self.assertEqual([v.version for v in versions], [0, 1])
        self.assertFalse(versions[0].is_current)
        self.assertTrue(versions[1].is_current)

    async def test_a_turn_without_edits_keeps_the_current_version(self):
        conversation = await self._create()
        claim = await self._claim(conversation.id)

        replay = await self._finish(claim)

        self.assertEqual(replay.version, 0)
        self.assertEqual(replay.changes, [])

    async def test_a_failed_turn_frees_the_conversation_and_allows_a_retry(self):
        conversation = await self._create()
        claim = await self._claim(conversation.id, turn_key="turn-1")

        await self.repository.fail_turn(claim=claim, error="bedrock timeout")

        async with self.database.session_factory() as session:
            stored = await session.get(Conversation, conversation.id)
            self.assertEqual(stored.status, ConversationStatus.ACTIVE)
            self.assertEqual(stored.turn_count, 0)

        retried = await self._claim(conversation.id, turn_key="turn-1")
        self.assertIsInstance(retried, TurnClaim)

    async def test_running_out_of_turns_exhausts_the_conversation(self):
        conversation = await self._create(max_turns=1)
        claim = await self._claim(conversation.id, turn_key="turn-1")
        replay = await self._finish(claim)

        self.assertEqual(replay.status, ConversationStatus.EXHAUSTED)
        self.assertEqual(replay.turns_left, 0)

        with self.assertRaises(ConversationExhausted):
            await self._claim(conversation.id, turn_key="turn-2")

    async def test_another_user_cannot_touch_the_conversation(self):
        conversation = await self._create()

        with self.assertRaises(ConversationNotOwned):
            await self.repository.claim_turn(
                conversation_id=conversation.id,
                user_id=OTHER_USER,
                message="hola",
                turn_key="turn-1",
                turn_timeout_seconds=600,
                history_pairs=4,
            )

    async def test_an_unknown_conversation_is_reported_as_missing(self):
        with self.assertRaises(ConversationNotFound):
            await self._claim("does-not-exist")

    async def test_marking_applied_is_idempotent(self):
        conversation = await self._create()

        first = await self.repository.mark_applied(
            conversation_id=conversation.id,
            user_id=USER,
            applied_resume_id="resume-9",
            score_after=78,
        )
        second = await self.repository.mark_applied(
            conversation_id=conversation.id,
            user_id=USER,
            applied_resume_id="resume-9",
            score_after=None,
        )

        self.assertEqual(first.status, ConversationStatus.APPLIED)
        self.assertEqual(second.score_after, 78)

    async def test_the_history_only_carries_the_last_pairs(self):
        conversation = await self._create()
        for index in range(6):
            claim = await self._claim(conversation.id, turn_key=f"turn-{index}")
            await self._finish(claim)

        claim = await self._claim(conversation.id, turn_key="turn-last")

        self.assertLessEqual(len(claim.history), 8)
        self.assertEqual(claim.history[-1]["role"], "assistant")

    async def _create(self, **overrides):
        defaults = dict(
            user_id=USER,
            job_id=JOB["id"],
            job_snapshot=JOB,
            base_resume_id="resume-1",
            base_resume=base_resume(),
            language="es",
            status=ConversationStatus.AWAITING_CONFIRMATION,
            llm_provider="bedrock",
            llm_model="amazon.nova-pro-v1:0",
            input_price_per_1m=Decimal("0.8"),
            output_price_per_1m=Decimal("3.2"),
            max_turns=10,
            max_steps=12,
            score_before=62,
            greeting="Miré el aviso.",
        )
        defaults.update(overrides)
        return await self.repository.create_conversation(**defaults)

    async def _claim(self, conversation_id, turn_key="turn-1", message="Dale"):
        return await self.repository.claim_turn(
            conversation_id=conversation_id,
            user_id=USER,
            message=message,
            turn_key=turn_key,
            turn_timeout_seconds=600,
            history_pairs=4,
        )

    async def _finish(self, claim, *, resume=None, changes=None, spans=None):
        return await self.repository.finish_turn(
            claim=claim,
            reply="Listo",
            warnings=[],
            resume=resume,
            changes=changes or [],
            spans=spans or [],
            job_requirements=["Kubernetes"],
        )


if __name__ == "__main__":
    unittest.main()
