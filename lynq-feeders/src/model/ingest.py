from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

MAX_JOBS_PER_CATEGORY = 50


class IngestOverrides(BaseModel):
    sources: Optional[list[str]] = None
    categories: Optional[list[str]] = None
    jobs_per_category: Optional[int] = Field(default=None, ge=1, le=MAX_JOBS_PER_CATEGORY)


class RunPlan(BaseModel):
    sources: list[str]
    categories: list[str]
    jobs_per_category: int
