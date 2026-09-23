from __future__ import annotations

import logging
import re

from agent.answer import TurnAnswer
from prompt.refusal import render as refusal

log = logging.getLogger(__name__)

INSTRUCTIONS_END = "<job_posting>"
SHINGLE_WORDS = 8
MARKERS = ("find_evidence", "apply_edit", "turnanswer", "job_posting", "system_prompt")
SPAN_NAME = "out_of_scope"
SPAN_REASON = "the answer echoed the instructions"

_WORDS = re.compile(r"[a-z0-9_]+")


def instructions_of(system_prompt: str) -> str:
    return system_prompt.split(INSTRUCTIONS_END)[0]


def _shingles(text: str) -> set[tuple[str, ...]]:
    words = _WORDS.findall(text.lower())
    return {
        tuple(words[index : index + SHINGLE_WORDS])
        for index in range(len(words) - SHINGLE_WORDS + 1)
    }


def leaks(text: str, instructions: str) -> bool:
    if not text:
        return False

    lowered = text.lower()
    if any(marker in lowered for marker in MARKERS):
        return True

    return bool(_shingles(text) & _shingles(instructions))


def enforce(answer: TurnAnswer, system_prompt: str, language: str) -> TurnAnswer:
    instructions = instructions_of(system_prompt)
    leaking = [answer.reply] + list(answer.warnings)

    if any(leaks(text, instructions) for text in leaking):
        log.warning("message= The answer echoed the instructions and was replaced")
        return TurnAnswer(reply=refusal(language), warnings=[])

    return answer
