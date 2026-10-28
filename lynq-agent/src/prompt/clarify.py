from __future__ import annotations

from prompt.family import PromptFamily
from prompt.tailor import language_name

_FAMILY = PromptFamily("clarify")


def reference(provider: str) -> str:
    return _FAMILY.reference(provider)


def render(
    provider: str, history: list[tuple[str, str]], message: str, language: str
) -> str:
    return _FAMILY.template(provider).render(
        history=history, message=message, language=language_name(language)
    )
