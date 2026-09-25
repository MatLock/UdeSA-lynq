from __future__ import annotations

import unittest
from decimal import Decimal
from unittest.mock import patch

from langchain_aws import ChatBedrockConverse
from langchain_ollama import ChatOllama

from config import Settings, reset_settings
from llm.factory import bedrock_guardrail_config, build_model
from llm.pricing import FREE, prices_for


def settings_with(**environment) -> Settings:
    reset_settings()
    with patch.dict("os.environ", environment, clear=False):
        return Settings()


class BuildModelTest(unittest.TestCase):

    def tearDown(self) -> None:
        reset_settings()

    def test_ollama_is_the_default_provider(self) -> None:
        model = build_model(settings_with(LLM_PROVIDER="ollama", OLLAMA_MODEL="qwen2.5:7b"))

        self.assertIsInstance(model, ChatOllama)
        self.assertEqual(model.model, "qwen2.5:7b")
        self.assertEqual(model.temperature, 0)

    def test_no_guardrail_is_configured_when_none_is_declared(self) -> None:
        settings = settings_with(LLM_PROVIDER="bedrock", BEDROCK_GUARDRAIL_ID="")

        self.assertIsNone(bedrock_guardrail_config(settings))

    def test_the_declared_guardrail_is_passed_to_bedrock(self) -> None:
        settings = settings_with(
            LLM_PROVIDER="bedrock",
            BEDROCK_MODEL_ID="amazon.nova-pro-v1:0",
            BEDROCK_GUARDRAIL_ID="gr-1234",
            BEDROCK_GUARDRAIL_VERSION="3",
        )

        self.assertEqual(
            bedrock_guardrail_config(settings),
            {
                "guardrailIdentifier": "gr-1234",
                "guardrailVersion": "3",
                "trace": "enabled",
            },
        )

    def test_bedrock_is_built_with_the_model_of_the_environment(self) -> None:
        model = build_model(
            settings_with(
                LLM_PROVIDER="bedrock",
                BEDROCK_MODEL_ID="amazon.nova-pro-v1:0",
                BEDROCK_REGION="us-east-2",
                BEDROCK_MAX_TOKENS="2048",
            )
        )

        self.assertIsInstance(model, ChatBedrockConverse)
        self.assertEqual(model.model_id, "amazon.nova-pro-v1:0")
        self.assertEqual(model.region_name, "us-east-2")
        self.assertEqual(model.max_tokens, 2048)
        self.assertEqual(model.temperature, 0)

    def test_bedrock_without_a_model_id_is_a_configuration_error(self) -> None:
        with self.assertRaises(ValueError):
            build_model(settings_with(LLM_PROVIDER="bedrock", BEDROCK_MODEL_ID=""))


class PricingTest(unittest.TestCase):

    def tearDown(self) -> None:
        reset_settings()

    def test_a_known_model_has_its_tariff(self) -> None:
        self.assertEqual(
            prices_for("amazon.nova-pro-v1:0"), (Decimal("0.8000"), Decimal("3.2000"))
        )

    def test_an_inference_profile_resolves_to_the_same_tariff(self) -> None:
        self.assertEqual(
            prices_for("us.amazon.nova-pro-v1:0"), prices_for("amazon.nova-pro-v1:0")
        )

    def test_an_unknown_model_costs_zero(self) -> None:
        self.assertEqual(prices_for("who.knows-v9:0"), FREE)

    def test_the_tariff_of_the_model_is_what_the_conversation_freezes(self) -> None:
        settings = settings_with(
            LLM_PROVIDER="bedrock", BEDROCK_MODEL_ID="amazon.nova-pro-v1:0"
        )

        self.assertEqual(settings.input_price_per_1m, Decimal("0.8000"))
        self.assertEqual(settings.output_price_per_1m, Decimal("3.2000"))

    def test_ollama_is_free(self) -> None:
        settings = settings_with(LLM_PROVIDER="ollama")

        self.assertEqual(settings.input_price_per_1m, Decimal("0"))
        self.assertEqual(settings.output_price_per_1m, Decimal("0"))
