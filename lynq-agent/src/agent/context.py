from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal

from agent.editor import ResumeEditor

STEP_LIMIT_MESSAGE = (
    "STEP_LIMIT_REACHED: you cannot make any more edits in this turn. "
    "Answer the user with what you have already applied."
)


@dataclass
class TurnContext:
    conversation_id: str
    job_snapshot: dict
    base_resume: dict
    editor: ResumeEditor
    max_steps: int
    language: str
    input_price_per_1m: Decimal
    output_price_per_1m: Decimal
    job_requirements: list[str] | None = None
    tool_steps: int = 0
    limit_reported: bool = False
    evidence_log: list[dict] = field(default_factory=list)

    @property
    def limit_reached(self) -> bool:
        return self.tool_steps >= self.max_steps

    def count_step(self) -> None:
        self.tool_steps += 1

    @property
    def resume(self) -> dict:
        return self.editor.resume

    @property
    def changes(self) -> list[dict]:
        return self.editor.applied_changes
