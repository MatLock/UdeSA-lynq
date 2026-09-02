"""Tests for the Ollama (httpx mocked) and Bedrock (boto3 mocked) clients."""

from __future__ import annotations

import unittest
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
from botocore.exceptions import ClientError, NoCredentialsError

from llm_client import bedrock_client as bedrock_module
from llm_client.base import LLMError
from llm_client.bedrock_client import BedrockClient
from llm_client.ollama_client import OllamaClient


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
    """Ollama talks to ``/api/chat`` and ``/api/version``."""

    async def test_generate_posts_user_message_and_returns_message_content(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="qwen2.5:7b")
        factory, session = _fake_async_client(
            response=_ok_response(
                {"message": {"role": "assistant", "content": '{"skills": ["Java"]}'}}
            )
        )

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            result = await client.generate("PROMPT")

        self.assertEqual(result, '{"skills": ["Java"]}')
        url, kwargs = session.post.await_args.args, session.post.await_args.kwargs
        self.assertEqual(url[0], "http://ollama:11434/api/chat")
        payload = kwargs["json"]
        self.assertEqual(payload["messages"], [{"role": "user", "content": "PROMPT"}])
        self.assertFalse(payload["stream"])
        self.assertEqual(payload["format"], "json")
        self.assertEqual(payload["model"], "qwen2.5:7b")
        # No raw mode: Ollama applies the model's own chat template.
        self.assertNotIn("raw", payload)
        self.assertNotIn("prompt", payload)

    async def test_health_check_true_when_version_ok(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="qwen2.5:7b")
        factory, session = _fake_async_client(response=_ok_response({"version": "1"}))

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertTrue(await client.health_check())

        self.assertEqual(session.get.await_args.args[0], "http://ollama:11434/api/version")

    async def test_health_check_false_on_transport_error(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="qwen2.5:7b")
        factory, _ = _fake_async_client(raises=httpx.ConnectError("down"))

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertFalse(await client.health_check())

    async def test_health_check_false_on_error_status(self) -> None:
        client = OllamaClient(base_url="http://ollama:11434", model="qwen2.5:7b")
        factory, _ = _fake_async_client(response=_error_response())

        with patch("llm_client.ollama_client.httpx.AsyncClient", factory):
            self.assertFalse(await client.health_check())


def _converse_response(text):
    return {"output": {"message": {"content": [{"text": text}]}}}


def _client_error(code):
    return ClientError({"Error": {"Code": code, "Message": "nope"}}, "Converse")


def _fake_boto(*, converse=None, converse_raises=None, list_raises=None):
    """Patch the cached boto3 client factory; returns (patcher, boto3 mock)."""
    boto = MagicMock()
    if converse_raises is not None:
        boto.converse = MagicMock(side_effect=converse_raises)
    else:
        boto.converse = MagicMock(return_value=converse)
    if list_raises is not None:
        boto.list_foundation_models = MagicMock(side_effect=list_raises)
    else:
        boto.list_foundation_models = MagicMock(return_value={"modelSummaries": []})

    return patch.object(bedrock_module, "_boto_client", MagicMock(return_value=boto)), boto


class BedrockClientTests(unittest.IsolatedAsyncioTestCase):
    """Bedrock talks to ``converse`` and probes with ``list_foundation_models``."""

    def setUp(self) -> None:
        bedrock_module._boto_client.cache_clear()

    def _client(self):
        return BedrockClient(
            model="amazon.nova-pro-v1:0", region="us-east-1", max_tokens=2048
        )

    async def test_generate_sends_single_user_turn_and_returns_text(self) -> None:
        patcher, boto = _fake_boto(
            converse=_converse_response('{"skills": ["Python"]}')
        )
        with patcher:
            result = await self._client().generate("PROMPT")

        self.assertEqual(result, '{"skills": ["Python"]}')
        kwargs = boto.converse.call_args.kwargs
        self.assertEqual(kwargs["modelId"], "amazon.nova-pro-v1:0")
        self.assertEqual(
            kwargs["messages"], [{"role": "user", "content": [{"text": "PROMPT"}]}]
        )
        self.assertEqual(kwargs["inferenceConfig"]["maxTokens"], 2048)
        self.assertEqual(kwargs["inferenceConfig"]["temperature"], 0.0)

    async def test_generate_joins_every_text_block(self) -> None:
        response = {
            "output": {
                "message": {"content": [{"text": '{"a":'}, {"text": " 1}"}]}
            }
        }
        patcher, _ = _fake_boto(converse=response)
        with patcher:
            self.assertEqual(await self._client().generate("P"), '{"a": 1}')

    async def test_generate_unwraps_a_markdown_json_fence(self) -> None:
        fenced = '```json\n{"skills": ["Java"]}\n```'
        patcher, _ = _fake_boto(converse=_converse_response(fenced))
        with patcher:
            self.assertEqual(
                await self._client().generate("P"), '{"skills": ["Java"]}'
            )

    async def test_generate_raises_llm_error_on_client_error(self) -> None:
        patcher, _ = _fake_boto(converse_raises=_client_error("ThrottlingException"))
        with patcher:
            with self.assertRaises(LLMError) as ctx:
                await self._client().generate("P")

        self.assertIn("Bedrock converse failed", str(ctx.exception))

    async def test_generate_raises_llm_error_without_credentials(self) -> None:
        patcher, _ = _fake_boto(converse_raises=NoCredentialsError())
        with patcher:
            with self.assertRaises(LLMError):
                await self._client().generate("P")

    async def test_generate_raises_llm_error_on_unexpected_shape(self) -> None:
        patcher, _ = _fake_boto(converse={"output": {}})
        with patcher:
            with self.assertRaises(LLMError) as ctx:
                await self._client().generate("P")

        self.assertIn("Unexpected Bedrock response shape", str(ctx.exception))

    async def test_generate_raises_llm_error_on_empty_completion(self) -> None:
        patcher, _ = _fake_boto(converse=_converse_response("   "))
        with patcher:
            with self.assertRaises(LLMError) as ctx:
                await self._client().generate("P")

        self.assertIn("empty completion", str(ctx.exception))

    async def test_health_check_true_when_models_listed(self) -> None:
        patcher, boto = _fake_boto(converse=_converse_response("{}"))
        with patcher:
            self.assertTrue(await self._client().health_check())

        boto.list_foundation_models.assert_called_once_with()

    async def test_health_check_true_when_only_the_listing_is_denied(self) -> None:
        patcher, _ = _fake_boto(list_raises=_client_error("AccessDeniedException"))
        with patcher:
            self.assertTrue(await self._client().health_check())

    async def test_health_check_false_on_other_client_errors(self) -> None:
        patcher, _ = _fake_boto(list_raises=_client_error("UnrecognizedClientException"))
        with patcher:
            self.assertFalse(await self._client().health_check())

    async def test_health_check_false_without_credentials(self) -> None:
        patcher, _ = _fake_boto(list_raises=NoCredentialsError())
        with patcher:
            self.assertFalse(await self._client().health_check())


if __name__ == "__main__":
    unittest.main()