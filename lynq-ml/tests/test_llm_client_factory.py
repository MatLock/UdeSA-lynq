"""Tests for the ``get_llm_client`` factory."""

from __future__ import annotations

import unittest
from unittest.mock import patch

from llm_client import LLMProvider, get_llm_client
from llm_client.bedrock_client import BedrockClient
from llm_client.ollama_client import OllamaClient


class GetLLMClientTests(unittest.TestCase):
    """The factory picks a client from environment variables."""

    def test_defaults_to_ollama_when_provider_unset(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OllamaClient)
        self.assertEqual(client.provider, LLMProvider.OLLAMA)
        self.assertEqual(client.base_url, "http://localhost:11434")
        self.assertEqual(client.model, "qwen2.5:7b")
        self.assertEqual(client.timeout, 300.0)

    def test_ollama_reads_overrides_from_env(self) -> None:
        env = {
            "LLM_PROVIDER": "ollama",
            "OLLAMA_BASE_URL": "http://ollama:11434/",
            "OLLAMA_MODEL": "mistral",
            "LLM_TIMEOUT": "30",
        }
        with patch.dict("os.environ", env, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OllamaClient)
        # base_url has its trailing slash stripped by the client.
        self.assertEqual(client.base_url, "http://ollama:11434")
        self.assertEqual(client.model, "mistral")
        self.assertEqual(client.timeout, 30.0)

    def test_bedrock_requires_model_id(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "bedrock"}, clear=True):
            with self.assertRaises(ValueError) as ctx:
                get_llm_client()

        self.assertIn("BEDROCK_MODEL_ID", str(ctx.exception))

    def test_bedrock_built_with_env_settings(self) -> None:
        env = {
            "LLM_PROVIDER": "bedrock",
            "BEDROCK_MODEL_ID": "amazon.nova-pro-v1:0",
            "BEDROCK_REGION": "sa-east-1",
            "BEDROCK_MAX_TOKENS": "1024",
            "BEDROCK_TEMPERATURE": "0.3",
            "BEDROCK_MAX_ATTEMPTS": "5",
            "LLM_TIMEOUT": "45",
        }
        with patch.dict("os.environ", env, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, BedrockClient)
        self.assertEqual(client.provider, LLMProvider.BEDROCK)
        self.assertEqual(client.model, "amazon.nova-pro-v1:0")
        self.assertEqual(client.region, "sa-east-1")
        self.assertEqual(client.max_tokens, 1024)
        self.assertEqual(client.temperature, 0.3)
        self.assertEqual(client.max_attempts, 5)
        self.assertEqual(client.timeout, 45.0)

    def test_bedrock_model_id_is_provider_agnostic(self) -> None:
        env = {
            "LLM_PROVIDER": "bedrock",
            "BEDROCK_MODEL_ID": "anthropic.claude-sonnet-4-5-20250929-v1:0",
        }
        with patch.dict("os.environ", env, clear=True):
            client = get_llm_client()

        self.assertEqual(client.model, "anthropic.claude-sonnet-4-5-20250929-v1:0")

    def test_bedrock_region_falls_back_to_aws_region_then_default(self) -> None:
        env = {
            "LLM_PROVIDER": "bedrock",
            "BEDROCK_MODEL_ID": "amazon.nova-lite-v1:0",
            "AWS_REGION": "eu-west-1",
        }
        with patch.dict("os.environ", env, clear=True):
            self.assertEqual(get_llm_client().region, "eu-west-1")

        del env["AWS_REGION"]
        with patch.dict("os.environ", env, clear=True):
            self.assertEqual(get_llm_client().region, "us-east-1")

    def test_bedrock_defaults(self) -> None:
        env = {"LLM_PROVIDER": "bedrock", "BEDROCK_MODEL_ID": "amazon.nova-lite-v1:0"}
        with patch.dict("os.environ", env, clear=True):
            client = get_llm_client()

        self.assertEqual(client.max_tokens, 4096)
        self.assertEqual(client.temperature, 0.0)
        self.assertEqual(client.max_attempts, 3)
        self.assertEqual(client.timeout, 300.0)

    def test_uppercase_provider_is_normalised(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "OLLAMA"}, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OllamaClient)

    def test_openai_is_no_longer_a_provider(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "openai"}, clear=True):
            with self.assertRaises(ValueError) as ctx:
                get_llm_client()

        self.assertIn("Unsupported LLM_PROVIDER", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()