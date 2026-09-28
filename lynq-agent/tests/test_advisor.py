from __future__ import annotations

import unittest

from agent.advisor import numbered
from agent.schemas import Advice, Recommendation


class NumberedTest(unittest.TestCase):

    def test_the_numbers_are_the_codes_in_the_order_the_model_gave(self) -> None:
        advice = numbered(
            Advice(reply="x", recommendations=[Recommendation(id=9, what="a"), Recommendation(id=9, what="b")])
        )

        self.assertEqual([(r.id, r.what) for r in advice.recommendations], [(1, "a"), (2, "b")])

    def test_an_empty_recommendation_is_dropped(self) -> None:
        advice = numbered(Advice(reply="x", recommendations=[Recommendation(what="  "), Recommendation(what="b")]))

        self.assertEqual([(r.id, r.what) for r in advice.recommendations], [(1, "b")])
