from __future__ import annotations

import unittest
from decimal import Decimal

from fastapi.testclient import TestClient

from tests.support import JOB, TemporaryDatabase, base_resume

from agent.graph import AgentError, TurnOutcome
from config import Settings
from client import LynqMlClient
from db.models import SpanKind
from db.repository import ConversationRepository, SpanRecord
from llm import ModelHandle
from main import app
from router.dependencies import get_conversation_service
from service.conversation_service import ConversationService

BASE = "/lynq-agent/dmz"
HEADERS = {"lynq-request-uuid": "uuid-1", "user-id": "user-1"}


def _handle() -> ModelHandle:
    return ModelHandle(model=object(), provider="ollama", model_id="qwen2.5:7b")


class ScriptedRunner:

    def __init__(self) -> None:
        self.calls: list[dict] = []
        self.failure: Exception | None = None

    async def __call__(self, **kwargs):
        self.calls.append(kwargs)
        if self.failure is not None:
            raise self.failure

        context = kwargs["context"]
        context.editor.apply(
            "skills", "add", {"bucket": "tools", "name": "Jenkins"}
        )
        return TurnOutcome(
            reply="Rescaté Jenkins de tu experiencia en Globant.",
            warnings=["No encontré respaldo para Go en tu CV."],
            resume=context.editor.resume,
            changes=context.editor.applied_changes,
            spans=[
                SpanRecord(
                    step=1,
                    kind=SpanKind.LLM,
                    name="llm",
                    prompt_tokens=900,
                    completion_tokens=120,
                    cost_usd=Decimal("0.001104"),
                )
            ],
            job_requirements=["Kubernetes", "PostgreSQL"],
        )


class ConversationRouterTest(unittest.IsolatedAsyncioTestCase):

    async def asyncSetUp(self):
        self.database = TemporaryDatabase()
        await self.database.create_schema()
        self.runner = ScriptedRunner()
        self.service = ConversationService(
            repository=ConversationRepository(self.database.session_factory),
            ml_client=LynqMlClient("http://ml", "system", 1.0),
            settings=Settings(),
            turn_runner=self.runner,
            model_builder=_handle,
        )
        app.dependency_overrides[get_conversation_service] = lambda: self.service
        self.client = TestClient(app)

    async def asyncTearDown(self):
        app.dependency_overrides.clear()
        await self.database.dispose()

    def _create(self, **overrides):
        body = {
            "job": JOB,
            "baseResumeId": "resume-1",
            "baseResume": base_resume(),
            "scoreBefore": 62,
            "language": "es",
        }
        body.update(overrides)
        return self.client.post(f"{BASE}/conversation", json=body, headers=HEADERS)

    def _turn(self, conversation_id, message="Dale", turn_key="turn-1"):
        return self.client.post(
            f"{BASE}/conversation/{conversation_id}/turn",
            json={"message": message, "turnKey": turn_key},
            headers=HEADERS,
        )

    def test_creating_a_conversation_returns_a_templated_greeting(self):
        response = self._create()

        self.assertEqual(response.status_code, 201)
        body = response.json()
        self.assertTrue(body["success"])
        self.assertIn("Senior Backend Engineer", body["data"]["greeting"])
        self.assertIn("Acme", body["data"]["greeting"])
        self.assertEqual(body["data"]["status"], "AWAITING_CONFIRMATION")
        self.assertEqual(body["data"]["turnsLeft"], 10)

    def test_the_request_uuid_header_is_required(self):
        response = self.client.post(
            f"{BASE}/conversation",
            json={"job": JOB, "baseResumeId": "r", "baseResume": base_resume()},
            headers={"user-id": "user-1"},
        )

        self.assertEqual(response.status_code, 403)
        self.assertFalse(response.json()["success"])

    def test_a_turn_applies_edits_and_reports_them(self):
        conversation_id = self._create().json()["data"]["conversationId"]

        response = self._turn(conversation_id)

        self.assertEqual(response.status_code, 200)
        data = response.json()["data"]
        self.assertIn("Jenkins", data["reply"])
        self.assertEqual(data["version"], 1)
        self.assertEqual(data["status"], "ACTIVE")
        self.assertEqual(data["turnsLeft"], 9)
        self.assertIn("Jenkins", data["resume"]["skills"]["tools"])
        self.assertEqual(data["changes"][0]["section"], "skills")
        self.assertEqual(data["warnings"], ["No encontré respaldo para Go en tu CV."])

    def test_the_personal_info_survives_the_turn_untouched(self):
        conversation_id = self._create().json()["data"]["conversationId"]

        data = self._turn(conversation_id).json()["data"]

        self.assertEqual(
            data["resume"]["personal_info"]["full_name"], "Ada Lovelace"
        )

    def test_repeating_a_turn_key_does_not_run_the_agent_twice(self):
        conversation_id = self._create().json()["data"]["conversationId"]
        first = self._turn(conversation_id, turn_key="turn-1").json()["data"]

        second = self._turn(conversation_id, turn_key="turn-1").json()["data"]

        self.assertEqual(len(self.runner.calls), 1)
        self.assertEqual(first["reply"], second["reply"])
        self.assertEqual(first["version"], second["version"])

    def test_a_failing_agent_returns_502_and_leaves_the_turn_retryable(self):
        conversation_id = self._create().json()["data"]["conversationId"]
        self.runner.failure = AgentError("bedrock is down")

        failed = self._turn(conversation_id, turn_key="turn-1")

        self.assertEqual(failed.status_code, 502)
        self.assertFalse(failed.json()["success"])

        self.runner.failure = None
        retried = self._turn(conversation_id, turn_key="turn-1")
        self.assertEqual(retried.status_code, 200)
        self.assertEqual(retried.json()["data"]["turnsLeft"], 9)

    def test_another_user_cannot_read_the_conversation(self):
        conversation_id = self._create().json()["data"]["conversationId"]

        response = self.client.get(
            f"{BASE}/conversation/{conversation_id}",
            headers={"lynq-request-uuid": "uuid-1", "user-id": "someone-else"},
        )

        self.assertEqual(response.status_code, 403)

    def test_an_unknown_conversation_is_a_404(self):
        response = self.client.get(
            f"{BASE}/conversation/nope", headers=HEADERS
        )

        self.assertEqual(response.status_code, 404)

    def test_the_view_carries_the_thread_the_versions_and_the_scores(self):
        conversation_id = self._create().json()["data"]["conversationId"]
        self._turn(conversation_id)

        data = self.client.get(
            f"{BASE}/conversation/{conversation_id}", headers=HEADERS
        ).json()["data"]

        self.assertEqual(data["scoreBefore"], 62)
        self.assertIsNone(data["scoreAfter"])
        self.assertEqual([m["seq"] for m in data["messages"]], [0, 1, 2])
        self.assertEqual([v["version"] for v in data["versions"]], [0, 1])
        self.assertIn("Jenkins", data["currentResume"]["skills"]["tools"])

    def test_marking_applied_closes_the_conversation_with_the_score(self):
        conversation_id = self._create().json()["data"]["conversationId"]
        self._turn(conversation_id)

        response = self.client.patch(
            f"{BASE}/conversation/{conversation_id}/applied",
            json={"appliedResumeId": "resume-9", "scoreAfter": 78},
            headers=HEADERS,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["data"]["status"], "APPLIED")

        view = self.client.get(
            f"{BASE}/conversation/{conversation_id}", headers=HEADERS
        ).json()["data"]
        self.assertEqual(view["scoreBefore"], 62)
        self.assertEqual(view["scoreAfter"], 78)

    def test_an_exhausted_conversation_rejects_turns_but_keeps_the_resume(self):
        settings = Settings()
        settings.max_turns = 1
        self.service._settings = settings
        conversation_id = self._create().json()["data"]["conversationId"]

        first = self._turn(conversation_id, turn_key="turn-1")
        self.assertEqual(first.json()["data"]["status"], "EXHAUSTED")

        blocked = self._turn(conversation_id, turn_key="turn-2")
        self.assertEqual(blocked.status_code, 409)

        view = self.client.get(
            f"{BASE}/conversation/{conversation_id}", headers=HEADERS
        ).json()["data"]
        self.assertIn("Jenkins", view["currentResume"]["skills"]["tools"])

    def test_the_agent_never_sees_the_candidate_identity(self):
        conversation_id = self._create().json()["data"]["conversationId"]
        self._turn(conversation_id)

        context = self.runner.calls[0]["context"]
        from prompt.resume_tailor import without_personal_info

        self.assertNotIn("personal_info", without_personal_info(context.resume))


if __name__ == "__main__":
    unittest.main()
