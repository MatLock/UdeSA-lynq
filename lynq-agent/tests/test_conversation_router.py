from __future__ import annotations

import unittest
from unittest.mock import AsyncMock

from fastapi.testclient import TestClient

from tests.support import RESUME

from main import app
from model.conversation import (
    AppliedResponse,
    ConversationView,
    CreateConversationResponse,
    TurnResponse,
)
from model.errors import AlreadyApplied, ConversationNotFound, TurnInProgress
from router.conversation import get_conversation_service

_BASE = "/lynq-agent/dmz/conversation"
_HEADERS = {"lynq-request-uuid": "req-1", "user-id": "user-1"}
_CONVERSATION_ID = "0195f2c1-0000-0000-0000-000000000001"

_CREATE_BODY = {
    "job": {
        "id": "job-1",
        "title": "Senior Backend Engineer",
        "description": "Kubernetes and PostgreSQL",
        "workType": "REMOTE",
        "skills": ["Kubernetes"],
    },
    "baseResumeId": "resume-1",
    "baseResume": RESUME,
    "language": "es",
    "resumeLanguage": "en",
}


class ConversationRouterTest(unittest.TestCase):

    def setUp(self) -> None:
        self.service = AsyncMock()
        app.dependency_overrides[get_conversation_service] = lambda: self.service
        self.client = TestClient(app)

    def tearDown(self) -> None:
        app.dependency_overrides.clear()

    def test_create_returns_the_envelope_in_camel_case(self) -> None:
        self.service.create.return_value = CreateConversationResponse(
            conversation_id=_CONVERSATION_ID,
            greeting="Hi",
            status="AWAITING_CONFIRMATION",
        )

        response = self.client.post(_BASE, json=_CREATE_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertTrue(payload["success"])
        self.assertEqual(payload["data"]["conversationId"], _CONVERSATION_ID)
        self.assertEqual(payload["data"]["status"], "AWAITING_CONFIRMATION")

    def test_create_passes_the_headers_to_the_service(self) -> None:
        self.service.create.return_value = CreateConversationResponse(
            conversation_id=_CONVERSATION_ID, greeting="Hi", status="AWAITING_CONFIRMATION"
        )

        self.client.post(_BASE, json=_CREATE_BODY, headers=_HEADERS)

        request, request_uuid, user_id = self.service.create.await_args.args
        self.assertEqual(request_uuid, "req-1")
        self.assertEqual(user_id, "user-1")
        self.assertEqual(request.base_resume_id, "resume-1")

    def test_create_without_the_user_id_header_is_a_400(self) -> None:
        response = self.client.post(
            _BASE, json=_CREATE_BODY, headers={"lynq-request-uuid": "req-1"}
        )

        self.assertEqual(response.status_code, 400)

    def test_turn_returns_the_tailored_resume(self) -> None:
        self.service.turn.return_value = TurnResponse(
            reply="done",
            resume=RESUME,
            changes=[{"section": "summary", "kind": "rewrite", "detail": "tightened"}],
            warnings=["I did not invent Go"],
            version=2,
            status="ACTIVE",
            turns_left=8,
        )

        response = self.client.post(
            f"{_BASE}/{_CONVERSATION_ID}/turn",
            json={"message": "Highlight Kubernetes", "turnKey": "k1"},
            headers=_HEADERS,
        )

        self.assertEqual(response.status_code, 200)
        data = response.json()["data"]
        self.assertEqual(data["turnsLeft"], 8)
        self.assertEqual(data["version"], 2)
        self.assertEqual(data["changes"][0]["section"], "summary")

    def test_turn_without_a_turn_key_is_a_400(self) -> None:
        response = self.client.post(
            f"{_BASE}/{_CONVERSATION_ID}/turn",
            json={"message": "Go"},
            headers=_HEADERS,
        )

        self.assertEqual(response.status_code, 400)

    def test_a_running_turn_answers_409_with_its_code(self) -> None:
        self.service.turn.side_effect = TurnInProgress()

        response = self.client.post(
            f"{_BASE}/{_CONVERSATION_ID}/turn",
            json={"message": "Go", "turnKey": "k1"},
            headers=_HEADERS,
        )

        self.assertEqual(response.status_code, 409)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["code"], "TURN_IN_PROGRESS")

    def test_an_unknown_conversation_answers_404(self) -> None:
        self.service.view.side_effect = ConversationNotFound(_CONVERSATION_ID)

        response = self.client.get(f"{_BASE}/{_CONVERSATION_ID}", headers=_HEADERS)

        self.assertEqual(response.status_code, 404)
        self.assertIsNone(response.json()["code"])

    def test_get_returns_the_thread(self) -> None:
        self.service.view.return_value = ConversationView(
            conversation_id=_CONVERSATION_ID,
            job_id="job-1",
            base_resume_id="018f9c3a-0000",
            status="ACTIVE",
            turn_count=1,
            turns_left=9,
            messages=[
                {
                    "seq": 1,
                    "role": "assistant",
                    "content": "Hi",
                    "warnings": [],
                    "createdOn": "2026-09-20T12:00:00",
                }
            ],
            current_resume=RESUME,
            versions=[],
        )

        response = self.client.get(f"{_BASE}/{_CONVERSATION_ID}", headers=_HEADERS)

        self.assertEqual(response.status_code, 200)
        data = response.json()["data"]
        self.assertEqual(data["turnsLeft"], 9)
        self.assertEqual(data["jobId"], "job-1")
        self.assertEqual(data["baseResumeId"], "018f9c3a-0000")
        self.assertEqual(data["messages"][0]["createdOn"], "2026-09-20T12:00:00")
        self.assertEqual(data["currentResume"], RESUME)

    def test_applied_returns_the_closed_status(self) -> None:
        self.service.mark_applied.return_value = AppliedResponse(status="APPLIED")

        response = self.client.patch(
            f"{_BASE}/{_CONVERSATION_ID}/applied",
            json={"appliedResumeId": "018fa1b2-0000"},
            headers=_HEADERS,
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["data"]["status"], "APPLIED")

    def test_applying_a_second_resume_answers_409(self) -> None:
        self.service.mark_applied.side_effect = AlreadyApplied("already applied")

        response = self.client.patch(
            f"{_BASE}/{_CONVERSATION_ID}/applied",
            json={"appliedResumeId": "another"},
            headers=_HEADERS,
        )

        self.assertEqual(response.status_code, 409)
        self.assertEqual(response.json()["code"], "ALREADY_APPLIED")


if __name__ == "__main__":
    unittest.main()
