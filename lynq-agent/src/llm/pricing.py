from __future__ import annotations

import logging
from decimal import Decimal

log = logging.getLogger(__name__)

FREE = (Decimal("0"), Decimal("0"))

_REGION_PREFIXES = ("us.", "eu.", "apac.", "us-gov.")

MODEL_PRICES_PER_1M: dict[str, tuple[Decimal, Decimal]] = {
    "amazon.nova-pro-v1:0": (Decimal("0.8000"), Decimal("3.2000")),
    "amazon.nova-lite-v1:0": (Decimal("0.0600"), Decimal("0.2400")),
    "amazon.nova-micro-v1:0": (Decimal("0.0350"), Decimal("0.1400")),
    "anthropic.claude-sonnet-4-5-20250929-v1:0": (
        Decimal("3.0000"),
        Decimal("15.0000"),
    ),
    "anthropic.claude-haiku-4-5-20251001-v1:0": (
        Decimal("1.0000"),
        Decimal("5.0000"),
    ),
}


def _without_region_prefix(model: str) -> str:
    for prefix in _REGION_PREFIXES:
        if model.startswith(prefix):
            return model[len(prefix) :]
    return model


def prices_for(model: str) -> tuple[Decimal, Decimal]:
    prices = MODEL_PRICES_PER_1M.get(_without_region_prefix(model.strip()))
    if prices is None:
        log.warning(
            "message= No price sheet for model %s, its turns are billed at zero",
            model,
        )
        return FREE
    return prices
