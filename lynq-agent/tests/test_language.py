from __future__ import annotations

import logging
import unittest
from unittest.mock import patch

from langdetect import LangDetectException

from agent.language import verify_resume_language

_SPANISH = {
    "summary": "Ingeniero de backend con ocho anios en sistemas distribuidos.",
    "work_experience": [
        {"description": "Lidere la migracion de los servicios de pagos a Kubernetes."}
    ],
}


class VerifyResumeLanguageTest(unittest.TestCase):

    def setUp(self) -> None:
        logging.disable(logging.NOTSET)

    def tearDown(self) -> None:
        logging.disable(logging.CRITICAL)

    def test_the_declared_language_always_wins(self) -> None:
        with patch("agent.language.detect", return_value="es") as detect:
            with self.assertLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(_SPANISH, "en"), "en")

        detect.assert_called_once()

    def test_a_mismatch_only_warns(self) -> None:
        with patch("agent.language.detect", return_value="es"):
            with self.assertLogs("agent.language", level="WARNING") as logs:
                verify_resume_language(_SPANISH, "en")

        self.assertIn("declared=en", logs.output[0])
        self.assertIn("detected=es", logs.output[0])

    def test_a_match_does_not_warn(self) -> None:
        with patch("agent.language.detect", return_value="es-AR"):
            with self.assertNoLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(_SPANISH, "es"), "es")

    def test_a_resume_without_prose_is_not_detected(self) -> None:
        with patch("agent.language.detect") as detect:
            self.assertEqual(verify_resume_language({"summary": "Dev"}, "es"), "es")

        detect.assert_not_called()

    def test_a_detection_failure_keeps_the_declared_language(self) -> None:
        with patch("agent.language.detect", side_effect=LangDetectException(0, "no")):
            with self.assertLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(_SPANISH, "en"), "en")

    def test_the_sample_takes_the_work_experience_descriptions(self) -> None:
        with patch("agent.language.detect", return_value="es") as detect:
            verify_resume_language(_SPANISH, "es")

        sample = detect.call_args.args[0]
        self.assertIn("Ingeniero de backend", sample)
        self.assertIn("Kubernetes", sample)


if __name__ == "__main__":
    unittest.main()
