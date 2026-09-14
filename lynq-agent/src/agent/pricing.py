from __future__ import annotations

import logging
from decimal import Decimal

log = logging.getLogger(__name__)

FREE = (Decimal("0"), Decimal("0"))

PRICES_USD_PER_1M: dict[str, tuple[Decimal, Decimal]] = {
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


def prices_for(provider: str, model: str) -> tuple[Decimal, Decimal]:
    if provider == "ollama":
        return FREE

    prices = PRICES_USD_PER_1M.get(model)
    if prices is None:
        log.warning(
            "message= No price table entry for model, billing it at zero, model=%s",
            model,
        )
        return FREE
    return prices


def cost_of(
    prompt_tokens: int,
    completion_tokens: int,
    input_price_per_1m: Decimal,
    output_price_per_1m: Decimal,
) -> Decimal:
    million = Decimal("1000000")
    inbound = Decimal(prompt_tokens) * input_price_per_1m / million
    outbound = Decimal(completion_tokens) * output_price_per_1m / million
    return inbound + outbound
