from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


class VerifyOverrides(BaseModel):
    sources: Optional[list[str]] = None


class VerifyPlan(BaseModel):
    sources: list[str]
    max_checks: int
