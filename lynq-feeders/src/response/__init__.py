"""Standard lynq REST response envelopes (mirrors lynq-app-backend / lynq-iam)."""

from __future__ import annotations

from .models import ErrorRestResponse, GlobalRestResponse

__all__ = ["GlobalRestResponse", "ErrorRestResponse"]