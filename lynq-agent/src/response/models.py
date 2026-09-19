from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


class GlobalRestResponse[T](BaseModel):

    success: bool = True
    data: Optional[T] = None


class ErrorRestResponse(BaseModel):

    success: bool = False
    data: Optional[object] = None
    reason: str
