"""LLM client package: provider clients and a config-driven factory."""

from __future__ import annotations

import os

from .base import LLMClient, LLMError, LLMProvider
from .bedrock_client import BedrockClient
from .ollama_client import OllamaClient

__all__ = ["LLMClient", "LLMError", "LLMProvider", "get_llm_client"]

_DEFAULT_REGION = "us-east-1"


def get_llm_client() -> LLMClient:
    """Build the LLM client selected by the ``LLM_PROVIDER`` env var.

    Defaults to Ollama. Provider-specific settings are read from the
    environment:

    - Ollama: ``OLLAMA_BASE_URL`` (default ``http://localhost:11434``),
      ``OLLAMA_MODEL`` (default ``llama3.1``).
    - Bedrock: ``BEDROCK_MODEL_ID`` (required — any Converse-capable model,
      e.g. ``anthropic.claude-sonnet-4-5-20250929-v1:0`` or
      ``amazon.nova-pro-v1:0``), ``BEDROCK_REGION`` (falls back to
      ``AWS_REGION``, then ``us-east-1``), ``BEDROCK_MAX_TOKENS``
      (default ``4096``), ``BEDROCK_TEMPERATURE`` (default ``0``),
      ``BEDROCK_MAX_ATTEMPTS`` (default ``3``). Credentials come from the
      standard AWS chain — env vars, profile, or the pod's IAM role.

    Shared: ``LLM_TIMEOUT`` seconds (default ``60``).

    Raises:
        ValueError: If ``LLM_PROVIDER`` is unknown, or required settings for
            the selected provider are missing.
    """
    raw_provider = os.getenv("LLM_PROVIDER", LLMProvider.OLLAMA.value).lower()
    try:
        provider = LLMProvider(raw_provider)
    except ValueError as exc:
        raise ValueError(f"Unsupported LLM_PROVIDER: {raw_provider!r}") from exc

    timeout = float(os.getenv("LLM_TIMEOUT", "60"))

    if provider is LLMProvider.OLLAMA:
        return OllamaClient(
            base_url=os.getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
            model=os.getenv("OLLAMA_MODEL", "llama3.1"),
            timeout=timeout,
        )

    model = os.getenv("BEDROCK_MODEL_ID")
    if not model:
        raise ValueError("BEDROCK_MODEL_ID is required when LLM_PROVIDER=bedrock")
    return BedrockClient(
        model=model,
        region=os.getenv("BEDROCK_REGION") or os.getenv("AWS_REGION") or _DEFAULT_REGION,
        timeout=timeout,
        max_tokens=int(os.getenv("BEDROCK_MAX_TOKENS", "4096")),
        temperature=float(os.getenv("BEDROCK_TEMPERATURE", "0")),
        max_attempts=int(os.getenv("BEDROCK_MAX_ATTEMPTS", "3")),
    )
