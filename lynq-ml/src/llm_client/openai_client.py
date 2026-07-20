"""LLM client backed by the OpenAI (or compatible) chat completions API."""

from __future__ import annotations

import httpx

from .base import LLMClient, LLMProvider


class OpenAIClient(LLMClient):
    """Calls the OpenAI ``/chat/completions`` endpoint.

    The ``skill_extractor/openai.jinja`` template is plain text and is sent as a
    single user message. JSON output is requested via ``response_format``.
    """

    provider = LLMProvider.OPENAI

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://api.openai.com/v1",
        timeout: float = 60.0,
    ) -> None:
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    async def generate(self, prompt: str) -> str:
        payload = {
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
            "response_format": {"type": "json_object"},
            "temperature": 0,
        }
        headers = {"Authorization": f"Bearer {self.api_key}"}
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions", json=payload, headers=headers
            )
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]

    async def health_check(self) -> bool:
        headers = {"Authorization": f"Bearer {self.api_key}"}
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/models", headers=headers)
                response.raise_for_status()
            return True
        except httpx.HTTPError:
            return False