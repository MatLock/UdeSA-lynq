"""Tests for the Ollama and OpenAI client HTTP behaviour (httpx mocked)."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx

from llm_client.ollama_client import OllamaClient
from llm_client.openai_client import OpenAIClient


def _fake_async_client(*, response=None, raises=None):
    """Return a mock usable as ``httpx.AsyncClient(...)`` context manager.

    ``response`` is returned from ``post``/``get``; ``raises`` (an exception)
    is raised instead to simulate transport failures.
    """
    session = MagicMock()
    if raises is not None:
        session.post = AsyncMock(side_effect=raises)
        session.get = AsyncMock(side_effect=raises)
    else:
        session.post = AsyncMock(return_value=response)
        session.get = AsyncMock(return_value=response)

    ctx = MagicMock()
    ctx.__aenter__ = AsyncMock(return_value=session)
    ctx.__aexit__ = AsyncMock(return_value=False)

    factory = MagicMock(return_value=ctx)
    return factory, session


def _ok_response(json_body):
    response = MagicMock()
    response.raise_for_status = MagicMock()
    response.json = MagicMock(return_value=json_body)
    return response


def _error_response():
    response = MagicMock()
    response.raise_for_status = MagicMock(
        side_effect=httpx.HTTPStatusError(
            "boom", request=MagicMock(), response=MagicMock()
        )
    )
    return response


class OllamaClientTests(unittest.IsolatedAsyncioTestCase):
    """Ollama talks to ``/api/generate`` and ``/api/version``."""

    async def test_generate_posts_raw_prompt_and_returns_response_field(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="llama3.1")
        factory, session = _fake_async_client(
            response=_ok_response({"response": '{"skills": ["Java"]}'})
        )

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            result = await client.generate("PROMPT")

        self.assertEqual(result, '{"skills": ["Java"]}')
        url, kwargs = session.post.await_args.args, session.post.await_args.kwargs
        self.assertEqual(url[0], "http://ollama:11434/api/generate")
        payload = kwargs["json"]
        self.assertEqual(payload["prompt"], "PROMPT")
        self.assertTrue(payload["raw"])
        self.assertFalse(payload["stream"])
        self.assertEqual(payload["model"], "llama3.1")

    async def test_health_check_true_when_version_ok(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="llama3.1")
        factory, session = _fake_async_client(response=_ok_response({"version": "1"}))

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertTrue(await client.health_check())

        self.assertEqual(session.get.await_args.args[0], "http://ollama:11434/api/version")

    async def test_health_check_false_on_transport_error(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="llama3.1")
        factory, _ = _fake_async_client(raises=httpx.ConnectError("down"))

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertFalse(await client.health_check())

    async def test_health_check_false_on_error_status(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="llama3.1")
        factory, _ = _fake_async_client(response=_error_response())

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertFalse(await client.health_check())


class OpenAIClientTests(unittest.IsolatedAsyncioTestCase):
    """OpenAI talks to ``/chat/completions`` and ``/models``."""

    async def test_generate_returns_message_content(self) -> None:
        client = OpenAIClient(api_key="sk-test", model="gpt-4o-mini")
        body = {"choices": [{"message": {"content": '{"skills": ["Python"]}'}}]}
        factory, session = _fake_async_client(response=_ok_response(body))

        with patch("llm_client.openai_client.httpx.AsyncClient", factory):
            result = await client.generate("PROMPT")

        self.assertEqual(result, '{"skills": ["Python"]}')
        args, kwargs = session.post.await_args.args, session.post.await_args.kwargs
        self.assertEqual(args[0], "https://api.openai.com/v1/chat/completions")
        self.assertEqual(kwargs["json"]["messages"][0]["content"], "PROMPT")
        self.assertEqual(kwargs["json"]["response_format"], {"type": "json_object"})
        self.assertEqual(kwargs["headers"]["Authorization"], "Bearer sk-test")

    async def test_health_check_true_when_models_ok(self) -> None:
        client = OpenAIClient(api_key="sk-test", model="gpt-4o-mini")
        factory, session = _fake_async_client(response=_ok_response({"data": []}))

        with patch("llm_client.openai_client.httpx.AsyncClient", factory):
            self.assertTrue(await client.health_check())

        self.assertEqual(session.get.await_args.args[0], "https://api.openai.com/v1/models")

    async def test_health_check_false_on_transport_error(self) -> None:
        client = OpenAIClient(api_key="sk-test", model="gpt-4o-mini")
        factory, _ = _fake_async_client(raises=httpx.ConnectError("down"))

        with patch("llm_client.openai_client.httpx.AsyncClient", factory):
            self.assertFalse(await client.health_check())


if __name__ == "__main__":
    unittest.main()