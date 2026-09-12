from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

MAX_JOBS_PER_RUBRO = 50


class IngestOverrides(BaseModel):
    sources: Optional[list[str]] = None
    rubros: Optional[list[str]] = None
    jobs_per_rubro: Optional[int] = Field(default=None, ge=1, le=MAX_JOBS_PER_RUBRO)


class RunPlan(BaseModel):
    sources: list[str]
    rubros: list[str]
    jobs_per_rubro: int
