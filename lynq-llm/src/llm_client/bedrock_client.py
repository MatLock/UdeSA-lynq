"""LLM client backed by Amazon Bedrock's Converse API."""

from __future__ import annotations

import asyncio
from functools import lru_cache
from typing import Any

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from .base import LLMClient, LLMError, LLMProvider

_RUNTIME_SERVICE = "bedrock-runtime"
_CONTROL_SERVICE = "bedrock"
_ACCESS_DENIED = "AccessDeniedException"
_FENCE = "```"


@lru_cache(maxsize=None)
def _boto_client(service: str, region: str, timeout: float, max_attempts: int):
    """Build (and reuse) a boto3 client; ``get_llm_client`` runs per request."""
    return boto3.client(
        service,
        region_name=region,
        config=Config(
            read_timeout=timeout,
            connect_timeout=min(timeout, 10.0),
            retries={"max_attempts": max_attempts, "mode": "standard"},
        ),
    )


class BedrockClient(LLMClient):
    """Calls ``bedrock-runtime.converse`` with the prompt as a single user turn.

    Converse is model-agnostic, so ``model`` selects Claude, Nova, Llama or any
    other Bedrock model without a code change. boto3 is synchronous, so every
    call is handed to a worker thread.
    """

    provider = LLMProvider.BEDROCK

    def __init__(
        self,
        model: str,
        region: str,
        timeout: float = 60.0,
        max_tokens: int = 4096,
        temperature: float = 0.0,
        max_attempts: int = 3,
    ) -> None:
        self.model = model
        self.region = region
        self.timeout = timeout
        self.max_tokens = max_tokens
        self.temperature = temperature
        self.max_attempts = max_attempts

    async def generate(self, prompt: str) -> str:
        try:
            response = await asyncio.to_thread(self._converse, prompt)
        except (BotoCoreError, ClientError) as exc:
            raise LLMError(f"Bedrock converse failed: {exc}") from exc
        return _completion_text(response)

    async def health_check(self) -> bool:
        try:
            await asyncio.to_thread(self._list_models)
            return True
        except ClientError as exc:
            return exc.response.get("Error", {}).get("Code") == _ACCESS_DENIED
        except BotoCoreError:
            return False

    def _converse(self, prompt: str) -> dict[str, Any]:
        return self._client(_RUNTIME_SERVICE).converse(
            modelId=self.model,
            messages=[{"role": "user", "content": [{"text": prompt}]}],
            inferenceConfig={
                "maxTokens": self.max_tokens,
                "temperature": self.temperature,
            },
        )

    def _list_models(self) -> dict[str, Any]:
        return self._client(_CONTROL_SERVICE).list_foundation_models()

    def _client(self, service: str):
        return _boto_client(service, self.region, self.timeout, self.max_attempts)


def _completion_text(response: dict[str, Any]) -> str:
    try:
        blocks = response["output"]["message"]["content"]
        text = "".join(block["text"] for block in blocks if "text" in block)
    except (KeyError, TypeError) as exc:
        raise LLMError(f"Unexpected Bedrock response shape: {response!r}") from exc

    text = text.strip()
    if not text:
        raise LLMError("Bedrock returned an empty completion")
    return _strip_fence(text)


def _strip_fence(text: str) -> str:
    """Unwrap a ```json ... ``` block; Converse has no JSON-only output mode."""
    if not text.startswith(_FENCE):
        return text

    body = text[len(_FENCE) :]
    if body.lower().startswith("json"):
        body = body[len("json") :]

    closing = body.rfind(_FENCE)
    if closing != -1:
        body = body[:closing]
    return body.strip()
