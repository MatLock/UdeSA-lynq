"""LLM client backed by a local/remote Ollama server."""

from __future__ import annotations

import httpx

from .base import LLMClient, LLMError, LLMProvider


class OllamaClient(LLMClient):
    """Calls Ollama's ``/api/chat`` endpoint with the prompt as a single user turn.

    Ollama applies the chat template shipped with whichever model ``model``
    names, so switching between models with different chat formats (Qwen's
    ChatML, Llama 3's header tokens, Mistral's instruction tags) needs no change
    here and none in the prompt templates: they are plain text and carry no
    special tokens of their own.
    """

    provider = LLMProvider.OLLAMA

    def __init__(self, base_url: str, model: str, timeout: float = 60.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout

    async def generate(self, prompt: str) -> str:
        payload = {
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
            "stream": False,
            "format": "json",
        }
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.post(f"{self.base_url}/api/chat", json=payload)
                response.raise_for_status()
                return response.json()["message"]["content"]
        except httpx.HTTPError as exc:
            raise LLMError(f"Ollama request failed: {exc}") from exc

    async def health_check(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/api/version")
                response.raise_for_status()
            return True
        except httpx.HTTPError:
            return False
