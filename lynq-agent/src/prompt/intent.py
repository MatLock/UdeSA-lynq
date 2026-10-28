from __future__ import annotations

from prompt.family import PromptFamily

_FAMILY = PromptFamily("intent")


def reference(provider: str) -> str:
    return _FAMILY.reference(provider)


def render(provider: str, history: list[tuple[str, str]], message: str) -> str:
    return _FAMILY.template(provider).render(history=history, message=message)
