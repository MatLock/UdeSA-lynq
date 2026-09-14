from __future__ import annotations

import unittest
from unittest.mock import patch

from tests.support import base_resume  # noqa: F401

from config import Settings, get_settings, reset_settings
from llm import LLMProvider, selected_model_id, selected_provider
from llm.factory import build_model
from router.dependencies import get_conversation_service, reset_conversation_service


class SettingsTest(unittest.TestCase):

    def tearDown(self):
        reset_settings()

    def test_the_defaults_match_the_documented_limits(self):
        with patch.dict("os.environ", {}, clear=True):
            settings = Settings()

        self.assertEqual(settings.max_steps, 12)
        self.assertEqual(settings.max_turns, 10)
        self.assertEqual(settings.turn_timeout_seconds, 600)

    def test_the_turn_timeout_outlives_the_gateway_read_timeout(self):
        with patch.dict("os.environ", {}, clear=True):
            settings = Settings()

        self.assertGreater(settings.turn_timeout_seconds, 310)

    def test_the_limits_are_overridable_so_they_can_be_exercised(self):
        with patch.dict(
            "os.environ",
            {"AGENT_MAX_STEPS": "2", "AGENT_MAX_TURNS": "2"},
            clear=True,
        ):
            settings = Settings()

        self.assertEqual(settings.max_steps, 2)
        self.assertEqual(settings.max_turns, 2)

    def test_a_junk_value_falls_back_instead_of_crashing_the_service(self):
        with patch.dict("os.environ", {"AGENT_MAX_STEPS": "doce"}, clear=True):
            settings = Settings()

        self.assertEqual(settings.max_steps, 12)

    def test_the_settings_are_read_once(self):
        reset_settings()
        self.assertIs(get_settings(), get_settings())


class LlmFactoryTest(unittest.TestCase):

    def test_ollama_is_the_default_provider(self):
        with patch.dict("os.environ", {}, clear=True):
            self.assertIs(selected_provider(), LLMProvider.OLLAMA)
            self.assertEqual(selected_model_id(), "qwen2.5:7b")

    def test_an_unknown_provider_is_rejected_at_startup(self):
        with patch.dict("os.environ", {"LLM_PROVIDER": "openai"}, clear=True):
            with self.assertRaises(ValueError):
                selected_provider()

    def test_bedrock_requires_its_model_id(self):
        with patch.dict("os.environ", {"LLM_PROVIDER": "bedrock"}, clear=True):
            with self.assertRaises(ValueError):
                selected_model_id()

    def test_the_bedrock_model_id_is_read_from_the_environment(self):
        with patch.dict(
            "os.environ",
            {"LLM_PROVIDER": "bedrock", "BEDROCK_MODEL_ID": "amazon.nova-pro-v1:0"},
            clear=True,
        ):
            self.assertEqual(selected_model_id(), "amazon.nova-pro-v1:0")

    def test_building_the_ollama_model_reports_its_provider_and_id(self):
        with patch.dict("os.environ", {"OLLAMA_MODEL": "qwen2.5:14b"}, clear=True):
            handle = build_model()

        self.assertEqual(handle.provider, "ollama")
        self.assertEqual(handle.model_id, "qwen2.5:14b")


class WiringTest(unittest.TestCase):

    def tearDown(self):
        reset_conversation_service()

    def test_the_service_is_built_once_and_reused(self):
        reset_conversation_service()

        self.assertIs(get_conversation_service(), get_conversation_service())

    def test_the_service_is_wired_with_the_configured_limits(self):
        reset_conversation_service()
        reset_settings()

        service = get_conversation_service()

        self.assertEqual(service._settings.max_turns, get_settings().max_turns)


if __name__ == "__main__":
    unittest.main()
