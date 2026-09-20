from __future__ import annotations

import unittest
from decimal import Decimal
from unittest.mock import AsyncMock

from sqlalchemy import func, select

from tests.support import JOB, RESUME, TemporaryDatabase

from agent.context import SpanRecord, TurnOutcome
from agent.graph import STUB_REPLY, run_turn
from client.lynq_ml_client import SkillExtractionFailed
from config import Settings, reset_settings
from db import repository
from db.models import (
    Conversation,
    ConversationStatus,
    Message,
    MessageRole,
    ResumeVersion,
    SpanKind,
    TraceSpan,
)
from model.conversation import AppliedRequest, CreateConversationRequest, TurnRequest
from model.errors import ConversationError, ErrorCode

USER = "user-1"
REQUEST_UUID = "req-1"


def settings_with(**overrides) -> Settings:
    reset_settings()
    settings = Settings()
    settings.llm_provider = "bedrock"
    settings.llm_model = "amazon.nova-pro-v1:0"
    settings.input_price_per_1m = Decimal("0.8000")
    settings.output_price_per_1m = Decimal("3.2000")
    for name, value in overrides.items():
        setattr(settings, name, value)
    return settings


def create_request(**overrides) -> CreateConversationRequest:
    payload = {
        "job": {
            "id": JOB["id"],
            "title": JOB["title"],
            "company": JOB["company"],
            "description": JOB["description"],
            "workType": JOB["workType"],
            "skills": JOB["skills"],
        },
        "baseResumeId": "resume-1",
        "baseResume": RESUME,
        "language": "es",
        "resumeLanguage": "en",
    }
    payload.update(overrides)
    return CreateConversationRequest.model_validate(payload)


class ConversationServiceTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self) -> None:
        self.database = TemporaryDatabase()
        await self.database.create_schema()
        self.lynq_ml = AsyncMock()
        self.lynq_ml.extract_skills.return_value = ["Kubernetes", "PostgreSQL"]

    async def asyncTearDown(self) -> None:
        await self.database.dispose()

    def service(self, loop_runner=run_turn, **settings):
        from service.conversation_service import ConversationService

        return ConversationService(
            session_factory=self.database.session_factory,
            lynq_ml_client=self.lynq_ml,
            settings=settings_with(**settings),
            loop_runner=loop_runner,
        )

    async def _create(self, service=None, **overrides):
        service = service or self.service()
        return await service.create(create_request(**overrides), REQUEST_UUID, USER)

    async def _count(self, entity) -> int:
        async with self.database.session_factory() as session:
            return await session.scalar(select(func.count()).select_from(entity))

    async def _conversation(self, conversation_id: str) -> Conversation:
        async with self.database.session_factory() as session:
            return await repository.load(session, conversation_id)

    async def _messages(self, conversation_id: str) -> list[Message]:
        async with self.database.session_factory() as session:
            return await repository.list_messages(session, conversation_id)

    async def _spans(self, conversation_id: str) -> list[TraceSpan]:
        async with self.database.session_factory() as session:
            result = await session.scalars(
                select(TraceSpan).where(TraceSpan.conversation_id == conversation_id)
            )
            return list(result)

    async def test_create_freezes_the_snapshot_and_greets(self) -> None:
        created = await self._create()

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.status, ConversationStatus.AWAITING_CONFIRMATION)
        self.assertEqual(
            conversation.job_snapshot["extractedSkills"], ["Kubernetes", "PostgreSQL"]
        )
        self.assertEqual(conversation.base_resume, RESUME)
        self.assertEqual(conversation.max_turns, 10)
        self.assertEqual(conversation.input_price_per_1m, Decimal("0.8000"))

        messages = await self._messages(created.conversation_id)
        self.assertEqual([m.role for m in messages], [MessageRole.ASSISTANT])
        self.assertEqual(messages[0].content, created.greeting)

    async def test_create_truncates_the_job_description(self) -> None:
        long_description = "x" * 9000
        created = await self._create(
            service=self.service(job_description_max_chars=100),
            job={
                "id": "job-2",
                "title": "Long",
                "description": long_description,
                "workType": "REMOTE",
                "skills": [],
            },
        )

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(len(conversation.job_snapshot["description"]), 100)

    async def test_create_falls_back_to_the_posting_skills(self) -> None:
        self.lynq_ml.extract_skills.side_effect = SkillExtractionFailed("lynq-ml down")

        created = await self._create()

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.job_snapshot["extractedSkills"], JOB["skills"])

    async def test_a_turn_writes_the_four_tables(self) -> None:
        service = self.service()
        created = await self._create(service)

        response = await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        self.assertEqual(response.reply, STUB_REPLY)
        self.assertEqual(response.version, 1)
        self.assertEqual(response.status, ConversationStatus.ACTIVE)
        self.assertEqual(response.turns_left, 9)

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.turn_count, 1)
        self.assertIsNone(conversation.run_token)
        self.assertEqual(conversation.llm_calls, 1)
        self.assertGreater(conversation.total_prompt_tokens, 0)
        self.assertGreater(conversation.cost_usd, Decimal("0"))

        messages = await self._messages(created.conversation_id)
        self.assertEqual(
            [m.role for m in messages],
            [MessageRole.ASSISTANT, MessageRole.USER, MessageRole.ASSISTANT],
        )

        spans = await self._spans(created.conversation_id)
        self.assertEqual(len(spans), 2)
        self.assertTrue(all(s.message_id == messages[1].id for s in spans))

        self.assertEqual(await self._count(ResumeVersion), 1)

    async def test_the_second_turn_flips_is_current(self) -> None:
        service = self.service()
        created = await self._create(service)

        await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )
        second = await service.turn(
            created.conversation_id, TurnRequest(message="More", turnKey="k2"), USER
        )

        self.assertEqual(second.version, 2)
        async with self.database.session_factory() as session:
            versions = await repository.list_versions(session, created.conversation_id)
        self.assertEqual([v.version for v in versions], [1, 2])
        self.assertEqual([v.is_current for v in versions], [False, True])

    async def test_the_same_turn_key_replays_the_stored_answer(self) -> None:
        service = self.service()
        created = await self._create(service)
        request = TurnRequest(message="Go", turnKey="k1")

        first = await service.turn(created.conversation_id, request, USER)
        replay = await service.turn(created.conversation_id, request, USER)

        self.assertEqual(replay.reply, first.reply)
        self.assertEqual(replay.version, first.version)
        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.turn_count, 1)
        self.assertEqual(await self._count(Message), 3)

    async def test_an_orphan_user_message_is_reused(self) -> None:
        service = self.service()
        created = await self._create(service)
        async with self.database.session_factory() as session:
            orphan = await repository.append_message(
                session,
                created.conversation_id,
                MessageRole.USER,
                "Go",
                turn_key="k1",
            )
            orphan_id = orphan.id
            await session.commit()

        response = await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        self.assertEqual(response.reply, STUB_REPLY)
        messages = await self._messages(created.conversation_id)
        self.assertEqual(len(messages), 3)
        self.assertEqual(messages[1].id, orphan_id)

    async def test_a_running_turn_is_rejected(self) -> None:
        service = self.service()
        created = await self._create(service)
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, created.conversation_id)
            await repository.claim_turn(session, conversation)
            await session.commit()

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.TURN_IN_PROGRESS)

    async def test_a_stale_running_turn_is_taken_over(self) -> None:
        service = self.service(turn_timeout_seconds=0)
        created = await self._create(service)
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, created.conversation_id)
            await repository.claim_turn(session, conversation)
            await session.commit()

        response = await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        self.assertEqual(response.status, ConversationStatus.ACTIVE)

    async def test_the_last_turn_exhausts_the_conversation(self) -> None:
        service = self.service(max_turns=1)
        created = await self._create(service)

        response = await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        self.assertEqual(response.status, ConversationStatus.EXHAUSTED)
        self.assertEqual(response.turns_left, 0)

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="More", turnKey="k2"), USER
            )
        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.CONVERSATION_EXHAUSTED)

    async def test_another_user_cannot_take_a_turn(self) -> None:
        service = self.service()
        created = await self._create(service)

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id,
                TurnRequest(message="Go", turnKey="k1"),
                "someone-else",
            )

        self.assertEqual(raised.exception.status_code, 403)

    async def test_an_unknown_conversation_is_a_404(self) -> None:
        with self.assertRaises(ConversationError) as raised:
            await self.service().view("nope", USER)

        self.assertEqual(raised.exception.status_code, 404)

    async def test_a_failing_loop_does_not_cost_a_turn(self) -> None:
        async def explode(context):
            context.spans.append(
                SpanRecord(
                    step=1, kind=SpanKind.LLM, name="tailor", output="half an answer"
                )
            )
            raise RuntimeError("the model is down")

        service = self.service(loop_runner=explode)
        created = await self._create(service)

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 502)

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.turn_count, 0)
        self.assertEqual(conversation.status, ConversationStatus.ACTIVE)
        self.assertIsNone(conversation.run_token)

        messages = await self._messages(created.conversation_id)
        self.assertEqual([m.role for m in messages], [MessageRole.ASSISTANT])

        spans = await self._spans(created.conversation_id)
        self.assertEqual(len(spans), 2)
        self.assertTrue(all(span.message_id is None for span in spans))
        self.assertEqual(spans[-1].kind, SpanKind.ERROR)

    async def test_a_retry_after_a_failure_runs_again(self) -> None:
        async def explode(context):
            raise RuntimeError("the model is down")

        created = await self._create(self.service(loop_runner=explode))
        with self.assertRaises(ConversationError):
            await self.service(loop_runner=explode).turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        response = await self.service().turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        self.assertEqual(response.version, 1)

    async def test_a_superseded_turn_persists_nothing(self) -> None:
        stolen = {}

        async def steal(context):
            async with self.database.session_factory() as session:
                conversation = await repository.load(session, context.conversation_id)
                await repository.claim_turn(session, conversation)
                await session.commit()
                stolen["token"] = conversation.run_token
            return TurnOutcome(reply="from the zombie", resume=RESUME)

        service = self.service(loop_runner=steal)
        created = await self._create(service)

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 502)
        self.assertEqual(raised.exception.code, ErrorCode.STALE_RUN)
        self.assertEqual(await self._count(ResumeVersion), 0)

        messages = await self._messages(created.conversation_id)
        self.assertEqual([m.role for m in messages], [MessageRole.ASSISTANT])

        spans = await self._spans(created.conversation_id)
        self.assertEqual([span.name for span in spans], ["stale_run"])

        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.run_token, stolen["token"])
        self.assertEqual(conversation.turn_count, 0)

    async def test_a_failure_after_being_superseded_touches_nothing(self) -> None:
        async def steal_and_explode(context):
            async with self.database.session_factory() as session:
                conversation = await repository.load(session, context.conversation_id)
                await repository.claim_turn(session, conversation)
                await session.commit()
            raise RuntimeError("the model is down")

        service = self.service(loop_runner=steal_and_explode)
        created = await self._create(service)

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 502)
        self.assertEqual(await self._spans(created.conversation_id), [])
        messages = await self._messages(created.conversation_id)
        self.assertEqual([m.role for m in messages], [MessageRole.ASSISTANT, MessageRole.USER])
        conversation = await self._conversation(created.conversation_id)
        self.assertEqual(conversation.status, ConversationStatus.RUNNING)

    async def test_a_turn_on_an_applied_conversation_is_rejected(self) -> None:
        service = self.service()
        created = await self._create(service)
        await service.mark_applied(
            created.conversation_id, AppliedRequest(appliedResumeId="r-9"), USER
        )

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.ALREADY_APPLIED)

    async def test_a_turn_on_an_abandoned_conversation_is_rejected(self) -> None:
        service = self.service()
        created = await self._create(service)
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, created.conversation_id)
            conversation.status = ConversationStatus.ABANDONED
            await session.commit()

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.CONVERSATION_EXHAUSTED)

    async def test_an_inconsistent_row_still_cannot_spend_a_turn(self) -> None:
        service = self.service(max_turns=1)
        created = await self._create(service)
        async with self.database.session_factory() as session:
            conversation = await repository.load(session, created.conversation_id)
            conversation.turn_count = 1
            conversation.status = ConversationStatus.ACTIVE
            await session.commit()

        with self.assertRaises(ConversationError) as raised:
            await service.turn(
                created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
            )

        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.CONVERSATION_EXHAUSTED)

    async def test_applied_is_idempotent_and_rejects_a_second_resume(self) -> None:
        service = self.service()
        created = await self._create(service)

        first = await service.mark_applied(
            created.conversation_id, AppliedRequest(appliedResumeId="r-9"), USER
        )
        again = await service.mark_applied(
            created.conversation_id, AppliedRequest(appliedResumeId="r-9"), USER
        )

        self.assertEqual(first.status, ConversationStatus.APPLIED)
        self.assertEqual(again.status, ConversationStatus.APPLIED)

        with self.assertRaises(ConversationError) as raised:
            await service.mark_applied(
                created.conversation_id, AppliedRequest(appliedResumeId="r-8"), USER
            )

        self.assertEqual(raised.exception.status_code, 409)
        self.assertEqual(raised.exception.code, ErrorCode.ALREADY_APPLIED)

    async def test_the_view_shows_the_thread_and_the_versions(self) -> None:
        service = self.service()
        created = await self._create(service)
        await service.turn(
            created.conversation_id, TurnRequest(message="Go", turnKey="k1"), USER
        )

        view = await service.view(created.conversation_id, USER)

        self.assertEqual(view.status, ConversationStatus.ACTIVE)
        self.assertEqual(view.turn_count, 1)
        self.assertEqual(view.turns_left, 9)
        self.assertEqual(len(view.messages), 3)
        self.assertEqual([v.version for v in view.versions], [1])
        self.assertEqual(view.current_resume, RESUME)

    async def test_the_view_of_a_fresh_conversation_shows_the_base_resume(self) -> None:
        created = await self._create()

        view = await self.service().view(created.conversation_id, USER)

        self.assertEqual(view.current_resume, RESUME)
        self.assertEqual(view.versions, [])


if __name__ == "__main__":
    unittest.main()
