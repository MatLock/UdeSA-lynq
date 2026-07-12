"""Tests for the ``get_llm_client`` factory."""

from __future__ import annotations

import unittest
from unittest.mock import patch

from llm_client import LLMProvider, get_llm_client
from llm_client.ollama_client import OllamaClient
from llm_client.openai_client import OpenAIClient


class GetLLMClientTests(unittest.TestCase):
    """The factory picks a client from environment variables."""

    def test_defaults_to_ollama_when_provider_unset(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OllamaClient)
        self.assertEqual(client.provider, LLMProvider.OLLAMA)
        self.assertEqual(client.base_url, "http://localhost:11434")
        self.assertEqual(client.model, "llama3.1")
        self.assertEqual(client.timeout, 60.0)

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

    def test_openai_requires_api_key(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "openai"}, clear=True):
            with self.assertRaises(ValueError) as ctx:
                get_llm_client()

        self.assertIn("OPENAI_API_KEY", str(ctx.exception))

    def test_openai_built_with_env_settings(self) -> None:
        env = {
            "LLM_PROVIDER": "openai",
            "OPENAI_API_KEY": "sk-test",
            "OPENAI_MODEL": "gpt-4o",
            "OPENAI_BASE_URL": "https://proxy.example.com/v1/",
        }
        with patch.dict("os.environ", env, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OpenAIClient)
        self.assertEqual(client.provider, LLMProvider.OPENAI)
        self.assertEqual(client.api_key, "sk-test")
        self.assertEqual(client.model, "gpt-4o")
        self.assertEqual(client.base_url, "https://proxy.example.com/v1")

    def test_uppercase_provider_is_normalised(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "OLLAMA"}, clear=True):
            client = get_llm_client()

        self.assertIsInstance(client, OllamaClient)

    def test_unknown_provider_raises(self) -> None:
        with patch.dict("os.environ", {"LLM_PROVIDER": "gemini"}, clear=True):
            with self.assertRaises(ValueError) as ctx:
                get_llm_client()

        self.assertIn("Unsupported LLM_PROVIDER", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()