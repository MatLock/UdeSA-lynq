from __future__ import annotations

import logging
import unittest
from unittest.mock import patch

from langdetect import LangDetectException

from tests.fixtures.spanish import RESUME as SPANISH_RESUME

from agent.language import verify_resume_language

class VerifyResumeLanguageTest(unittest.TestCase):

    def setUp(self) -> None:
        logging.disable(logging.NOTSET)

    def tearDown(self) -> None:
        logging.disable(logging.CRITICAL)

    def test_the_declared_language_always_wins(self) -> None:
        with patch("agent.language.detect", return_value="es") as detect:
            with self.assertLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(SPANISH_RESUME, "en"), "en")

        detect.assert_called_once()

    def test_a_mismatch_only_warns(self) -> None:
        with patch("agent.language.detect", return_value="es"):
            with self.assertLogs("agent.language", level="WARNING") as logs:
                verify_resume_language(SPANISH_RESUME, "en")

        self.assertIn("declared=en", logs.output[0])
        self.assertIn("detected=es", logs.output[0])

    def test_a_match_does_not_warn(self) -> None:
        with patch("agent.language.detect", return_value="es-AR"):
            with self.assertNoLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(SPANISH_RESUME, "es"), "es")

    def test_a_resume_without_prose_is_not_detected(self) -> None:
        with patch("agent.language.detect") as detect:
            self.assertEqual(verify_resume_language({"summary": "Dev"}, "es"), "es")

        detect.assert_not_called()

    def test_a_detection_failure_keeps_the_declared_language(self) -> None:
        with patch("agent.language.detect", side_effect=LangDetectException(0, "no")):
            with self.assertLogs("agent.language", level="WARNING"):
                self.assertEqual(verify_resume_language(SPANISH_RESUME, "en"), "en")

    def test_the_sample_takes_the_work_experience_descriptions(self) -> None:
        with patch("agent.language.detect", return_value="es") as detect:
            verify_resume_language(SPANISH_RESUME, "es")

        sample = detect.call_args.args[0]
        self.assertIn("Ingeniero de backend", sample)
        self.assertIn("Kubernetes", sample)


if __name__ == "__main__":
    unittest.main()
