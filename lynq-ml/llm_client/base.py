"""Abstractions shared by the LLM client implementations."""

from __future__ import annotations

from abc import ABC, abstractmethod
from enum import Enum


class LLMProvider(str, Enum):
    """Supported LLM backends."""

    OLLAMA = "ollama"
    OPENAI = "openai"


class LLMClient(ABC):
    """Common interface every LLM backend client must implement."""

    #: The provider this client talks to. Also selects the prompt template.
    provider: LLMProvider

    @abstractmethod
    async def generate(self, prompt: str) -> str:
        """Send ``prompt`` to the model and return its raw text completion.

        Args:
            prompt: The fully rendered prompt to send to the model.

        Returns:
            The model's raw completion text (expected to be JSON for the
            key-extractor prompts).
        """

    @abstractmethod
    async def health_check(self) -> bool:
        """Check whether the backend is reachable and usable.

        Returns:
            ``True`` if the provider responded successfully, ``False`` if it
            was unreachable or returned an error.
        """