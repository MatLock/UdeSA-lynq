from __future__ import annotations

import unittest

from agent.advisor import listed, numbered
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


class ListedTest(unittest.TestCase):

    RECOMMENDATIONS = [
        Recommendation(id=1, what="Name Kubernetes in the summary"),
        Recommendation(id=2, what="Say what ran on Kubernetes at Acme"),
    ]

    def test_the_numbered_list_is_appended_to_the_reply(self) -> None:
        advice = listed(Advice(reply="Two things would help:", recommendations=self.RECOMMENDATIONS))

        self.assertEqual(
            advice.reply,
            "Two things would help:\n\n1. Name Kubernetes in the summary\n2. Say what ran on Kubernetes at Acme",
        )

    def test_a_list_the_model_wrote_itself_is_replaced_not_doubled(self) -> None:
        reply = "Two things would help:\n1) Name Kubernetes in your summary.\n- Say what ran on Kubernetes at Acme\nAsk me for either."
        advice = listed(Advice(reply=reply, recommendations=self.RECOMMENDATIONS))

        self.assertEqual(
            advice.reply,
            "Two things would help:\nAsk me for either.\n\n1. Name Kubernetes in the summary\n2. Say what ran on Kubernetes at Acme",
        )

    def test_a_reply_that_is_only_the_list_becomes_the_list(self) -> None:
        advice = listed(Advice(reply="1. Name Kubernetes in the summary", recommendations=self.RECOMMENDATIONS[:1]))

        self.assertEqual(advice.reply, "1. Name Kubernetes in the summary")

    def test_without_recommendations_the_reply_is_left_alone(self) -> None:
        advice = listed(Advice(reply="Nothing left to change.\n\nApply it as it is."))

        self.assertEqual(advice.reply, "Nothing left to change.\n\nApply it as it is.")
