"""LLM client backed by a local/remote Ollama server."""

from __future__ import annotations

import httpx

from .base import LLMClient, LLMProvider


class OllamaClient(LLMClient):
    """Calls Ollama's ``/api/generate`` endpoint in raw mode.

    The ``key_extractor/ollama.jinja`` template already embeds the model's
    chat special tokens, so requests are sent with ``raw=True`` to bypass
    Ollama's own chat templating.
    """

    provider = LLMProvider.OLLAMA

    def __init__(self, base_url: str, model: str, timeout: float = 60.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout

    async def generate(self, prompt: str) -> str:
        payload = {
            "model": self.model,
            "prompt": prompt,
            "raw": True,
            "stream": False,
            "format": "json",
        }
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(f"{self.base_url}/api/generate", json=payload)
            response.raise_for_status()
            return response.json()["response"]

    async def health_check(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/api/version")
                response.raise_for_status()
            return True
        except httpx.HTTPError:
            return False