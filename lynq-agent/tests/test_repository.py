from __future__ import annotations

import unittest
from decimal import Decimal

from sqlalchemy import select

from tests.support import RESUME, TemporaryDatabase, new_conversation

from agent.context import SpanRecord
from db import repository
from db.models import (
    ConversationStatus,
    MessageRole,
    ResumeVersion,
    SpanKind,
    TraceSpan,
)

_CHANGES = [{"section": "summary", "kind": "rewrite", "detail": "tightened"}]


class RepositoryTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        self.database = TemporaryDatabase()
        await self.database.create_schema()
        self.conversation = new_conversation(turn_count=0, max_turns=2)
        async with self.database.session_factory() as session:
            await repository.create_conversation(session, self.conversation)
            await session.commit()

    async def asyncTearDown(self) -> None:
        await self.database.dispose()

    async def test_the_sequence_follows_the_thread(self) -> None:
        async with self.database.session_factory() as session:
            first = await repository.append_message(
                session, self.conversation.id, MessageRole.ASSISTANT, "Hi"
            )
            second = await repository.append_message(
                session, self.conversation.id, MessageRole.USER, "Go", turn_key="k1"
            )
            await session.commit()

            self.assertEqual([first.seq, second.seq], [1, 2])
            self.assertEqual(
                await repository.next_seq(session, self.conversation.id), 3
            )

    async def test_the_reply_of_a_turn_is_the_next_assistant_message(self) -> None:
        async with self.database.session_factory() as session:
            user = await repository.append_message(
                session, self.conversation.id, MessageRole.USER, "Go", turn_key="k1"
            )
            await repository.append_message(
                session, self.conversation.id, MessageRole.ASSISTANT, "Done"
            )
            await session.commit()

            reply = await repository.find_reply_after(
                session, self.conversation.id, user.seq
            )
            self.assertEqual(reply.content, "Done")

    async def test_an_unanswered_turn_has_no_reply(self) -> None:
        async with self.database.session_factory() as session:
            user = await repository.append_message(
                session, self.conversation.id, MessageRole.USER, "Go", turn_key="k1"
            )
            await session.commit()

            self.assertIsNone(
                await repository.find_reply_after(
                    session, self.conversation.id, user.seq
                )
            )

    async def test_only_the_last_messages_are_replayed_into_the_prompt(self) -> None:
        async with self.database.session_factory() as session:
            for index in range(10):
                await repository.append_message(
                    session, self.conversation.id, MessageRole.USER, f"m{index}"
                )
            await session.commit()

            recent = await repository.recent_messages(session, self.conversation.id, 4)

        self.assertEqual([m.content for m in recent], ["m6", "m7", "m8", "m9"])

    async def test_saving_a_version_demotes_the_previous_one(self) -> None:
        async with self.database.session_factory() as session:
            first = await repository.save_version(
                session, self.conversation.id, RESUME, _CHANGES, produced_by="m1"
            )
            second = await repository.save_version(
                session, self.conversation.id, RESUME, _CHANGES, produced_by="m2"
            )
            await session.commit()

            versions = await session.scalars(
                select(ResumeVersion).order_by(ResumeVersion.version)
            )
            stored = list(versions)

        self.assertEqual([first.version, second.version], [1, 2])
        self.assertEqual([v.is_current for v in stored], [False, True])

    async def test_a_version_can_be_traced_back_to_its_message(self) -> None:
        async with self.database.session_factory() as session:
            await repository.save_version(
                session, self.conversation.id, RESUME, _CHANGES, produced_by="m1"
            )
            await session.commit()

            found = await repository.version_produced_by(session, "m1")

        self.assertEqual(found.changes, _CHANGES)

    async def test_spans_are_stored_with_their_measurements(self) -> None:
        record = SpanRecord(
            step=1,
            kind=SpanKind.LLM,
            name="tailor",
            input="the prompt",
            output="the answer",
            prompt_tokens=1200,
            completion_tokens=300,
            cost_usd=Decimal("0.00204000"),
            latency_ms=1800,
        )

        async with self.database.session_factory() as session:
            await repository.save_spans(session, self.conversation.id, "m1", [record])
            await session.commit()

            stored = await session.scalar(select(TraceSpan))

        self.assertEqual(stored.message_id, "m1")
        self.assertEqual(stored.prompt_tokens, 1200)
        self.assertEqual(stored.cost_usd, Decimal("0.00204000"))
        self.assertEqual(stored.latency_ms, 1800)

    async def test_closing_a_turn_rolls_the_cost_up(self) -> None:
        records = [
            SpanRecord(
                step=1,
                kind=SpanKind.LLM,
                name="tailor",
                prompt_tokens=1000,
                completion_tokens=200,
                cost_usd=Decimal("0.00144000"),
            ),
            SpanRecord(step=2, kind=SpanKind.TOOL, name="apply_edit"),
        ]

        async with self.database.session_factory() as session:
            conversation = await repository.load(session, self.conversation.id)
            await repository.close_turn(session, conversation, records)
            await session.commit()

        self.assertEqual(conversation.turn_count, 1)
        self.assertEqual(conversation.status, ConversationStatus.ACTIVE)
        self.assertEqual(conversation.llm_calls, 1)
        self.assertEqual(conversation.total_prompt_tokens, 1000)
        self.assertEqual(conversation.total_completion_tokens, 200)
        self.assertEqual(conversation.cost_usd, Decimal("0.00144000"))
        self.assertIsNone(conversation.run_token)

    async def test_the_last_turn_closes_the_conversation(self) -> None:
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, self.conversation.id)
            await repository.close_turn(session, conversation, [])
            await repository.close_turn(session, conversation, [])
            await session.commit()

        self.assertEqual(conversation.turn_count, 2)
        self.assertEqual(conversation.status, ConversationStatus.EXHAUSTED)

    async def test_claiming_and_releasing_a_turn(self) -> None:
        async with self.database.session_factory() as session:
            conversation = await repository.load_for_turn(session, self.conversation.id)
            token = await repository.claim_turn(session, conversation)
            self.assertEqual(conversation.status, ConversationStatus.RUNNING)
            self.assertEqual(conversation.run_token, token)

            await repository.release_turn(session, conversation)
            await session.commit()

        self.assertEqual(conversation.status, ConversationStatus.ACTIVE)
        self.assertIsNone(conversation.run_token)

    async def test_marking_a_conversation_as_applied(self) -> None:
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, self.conversation.id)
            await repository.mark_applied(session, conversation, "resume-9")
            await session.commit()

        self.assertEqual(conversation.status, ConversationStatus.APPLIED)
        self.assertEqual(conversation.applied_resume_id, "resume-9")


if __name__ == "__main__":
    unittest.main()
