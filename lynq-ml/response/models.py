"""Response envelopes matching the lynq ``GlobalRestResponse`` standard.

Mirrors the Java classes in lynq-app-backend / lynq-iam:

- Success: ``{ "success": true, "data": <payload> }``
- Error:   ``{ "success": false, "data": <null|fields>, "reason": "<message>" }``
"""

from __future__ import annotations

from typing import Generic, Optional, TypeVar

from pydantic import BaseModel

T = TypeVar("T")


class GlobalRestResponse(BaseModel, Generic[T]):
    """Standard success envelope wrapping a typed ``data`` payload."""

    success: bool = True
    data: Optional[T] = None


class ErrorRestResponse(BaseModel):
    """Standard error envelope: ``success=false`` plus a ``reason`` message.

    ``data`` optionally carries structured detail (e.g. per-field validation
    errors), matching ``ErrorRestResponse`` in the Java services.
    """

    success: bool = False
    data: Optional[object] = None
    reason: str