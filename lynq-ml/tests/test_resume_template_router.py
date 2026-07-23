"""Tests for the ``POST /lynq-ml/resume-template-creation`` endpoint."""

from __future__ import annotations

import unittest
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

from main import app

_ENDPOINT = "/lynq-ml/resume-template-creation"

_HEADERS = {
    "lynq-request-uuid": "req-123",
    "user-id": "user-1",
}

_RESUME = {
    "personal_info": {"full_name": "Juan Pérez", "headline": "Senior Backend Engineer"},
    "summary": "Backend engineer.",
    "skills": {"technical": ["Java"], "soft": ["Leadership"]},
}

_BODY = {
    "resume_content": _RESUME,
    "profile_url": "https://s3.example.com/photo.png?sig=abc",
    "put_resume_url": "https://s3.example.com/cv.pdf?sig=put",
}

_PDF = b"%PDF-1.7 fake pdf bytes"


class ResumeTemplateCreationRouterTests(unittest.TestCase):
    """Covers the happy path plus render/upload/validation failure branches."""

    def setUp(self) -> None:
        self.client = TestClient(app)

    def test_returns_201_and_uploads_pdf(self) -> None:
        upload = MagicMock(return_value=200)

        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ) as render, patch(
            "router.resume_template.upload_to_presigned_url", upload
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 201)
        self.assertTrue(response.json()["success"])
        # Rendered with the resume, the default template and the profile photo.
        render.assert_called_once()
        # Uploaded the rendered bytes to the presigned PUT URL as a PDF.
        upload.assert_called_once_with(
            _BODY["put_resume_url"], _PDF, "application/pdf"
        )

    def test_defaults_to_modern_template(self) -> None:
        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ) as render, patch("router.resume_template.upload_to_presigned_url"):
            self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        template_arg = render.call_args.args[1]
        self.assertEqual(template_arg.value, "MODERN")

    def test_honours_explicit_template(self) -> None:
        body = {**_BODY, "template": "CLASSIC"}

        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ) as render, patch("router.resume_template.upload_to_presigned_url"):
            self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(render.call_args.args[1].value, "CLASSIC")

    def test_profile_url_is_optional(self) -> None:
        body = {"resume_content": _RESUME, "put_resume_url": _BODY["put_resume_url"]}

        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ) as render, patch("router.resume_template.upload_to_presigned_url"):
            response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 201)
        self.assertIsNone(render.call_args.args[2])  # photo_url passed as None

    def test_returns_500_when_render_fails(self) -> None:
        with patch(
            "router.resume_template.render_resume_pdf",
            side_effect=RuntimeError("weasyprint blew up"),
        ), patch("router.resume_template.upload_to_presigned_url") as upload:
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 500)
        self.assertEqual(response.json()["reason"], "Failed to render resume PDF")
        upload.assert_not_called()

    def test_returns_502_when_upload_fails(self) -> None:
        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ), patch(
            "router.resume_template.upload_to_presigned_url",
            side_effect=OSError("connection refused"),
        ):
            response = self.client.post(_ENDPOINT, json=_BODY, headers=_HEADERS)

        self.assertEqual(response.status_code, 502)
        self.assertIn("Failed to upload resume PDF", response.json()["reason"])

    def test_invalid_template_returns_400(self) -> None:
        body = {**_BODY, "template": "FANCY"}

        response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        payload = response.json()
        self.assertFalse(payload["success"])
        self.assertEqual(payload["reason"], "Invalid Fields Found")
        self.assertIn("template", payload["data"])

    def test_missing_put_url_returns_400(self) -> None:
        body = {"resume_content": _RESUME}

        response = self.client.post(_ENDPOINT, json=body, headers=_HEADERS)

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")

    def test_missing_required_headers_returns_400(self) -> None:
        with patch(
            "router.resume_template.render_resume_pdf", return_value=_PDF
        ), patch("router.resume_template.upload_to_presigned_url"):
            response = self.client.post(
                _ENDPOINT, json=_BODY, headers={"lynq-request-uuid": "req-123"}
            )

        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["reason"], "Invalid Fields Found")


if __name__ == "__main__":
    unittest.main()
