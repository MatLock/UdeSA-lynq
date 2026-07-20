"""LLM client package: provider clients and a config-driven factory."""

from __future__ import annotations

import os

from .base import LLMClient, LLMProvider
from .ollama_client import OllamaClient
from .openai_client import OpenAIClient

__all__ = ["LLMClient", "LLMProvider", "get_llm_client"]


def get_llm_client() -> LLMClient:
    """Build the LLM client selected by the ``LLM_PROVIDER`` env var.

    Defaults to Ollama. Provider-specific settings are read from the
    environment:

    - Ollama: ``OLLAMA_BASE_URL`` (default ``http://localhost:11434``),
      ``OLLAMA_MODEL`` (default ``llama3``).
    - OpenAI: ``OPENAI_API_KEY`` (required), ``OPENAI_MODEL``
      (default ``gpt-4o-mini``), ``OPENAI_BASE_URL``
      (default ``https://api.openai.com/v1``).

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

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise ValueError("OPENAI_API_KEY is required when LLM_PROVIDER=openai")
    return OpenAIClient(
        api_key=api_key,
        model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
        base_url=os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1"),
        timeout=timeout,
    )