from __future__ import annotations

import unittest
from decimal import Decimal

from tests.support import TemporaryDatabase  # noqa: F401

from agent.pricing import FREE, cost_of, prices_for


class PricingTest(unittest.TestCase):

    def test_a_known_bedrock_model_has_its_rates(self):
        self.assertEqual(
            prices_for("bedrock", "amazon.nova-pro-v1:0"),
            (Decimal("0.8000"), Decimal("3.2000")),
        )

    def test_ollama_is_always_free(self):
        self.assertEqual(prices_for("ollama", "qwen2.5:7b"), FREE)

    def test_an_unknown_model_bills_zero_instead_of_failing(self):
        self.assertEqual(prices_for("bedrock", "some.new-model-v9:0"), FREE)

    def test_the_cost_is_exact_decimal_arithmetic(self):
        cost = cost_of(1000, 200, Decimal("0.8"), Decimal("3.2"))

        self.assertEqual(cost, Decimal("0.00144"))
        self.assertIsInstance(cost, Decimal)

    def test_a_free_provider_costs_nothing(self):
        self.assertEqual(cost_of(9999, 9999, *FREE), Decimal("0"))


if __name__ == "__main__":
    unittest.main()
