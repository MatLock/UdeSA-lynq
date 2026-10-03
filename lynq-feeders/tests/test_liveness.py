from __future__ import annotations

import json
import pathlib
import unittest
from unittest.mock import MagicMock, patch

import requests

from scraper import (
    BumeranLivenessChecker,
    ComputrabajoLivenessChecker,
    LivenessOutcome,
    get_liveness_checkers,
)
from scraper.bumeran import FICHA_URL, aviso_id_of, classify_ficha
from scraper.computrabajo import classify_offer_page, classify_offer_response, is_offer_url

FIXTURES = pathlib.Path(__file__).parent / "fixtures" / "liveness"

BUMERAN_URL = "https://www.bumeran.com.ar/empleos/desarrollador-python-sr-hibrido-cap-federal-1118438425.html"
OFFER_URL = (
    "https://ar.computrabajo.com/ofertas-de-trabajo/"
    "oferta-de-trabajo-de-auxiliar-de-sistemas-en-neuquen-5DD70CA6047908F761373E686DCF3405"
)
LISTING_REDIRECT = (
    "https://ar.computrabajo.com/trabajo-de-auxiliar-de-sistemas-en-neuquen"
    "#5DD70CA6047908F761373E686DCF3405&domv"
)
CHALLENGE = "<!DOCTYPE html><html><head><title>Attention Required! | Cloudflare</title>"


def _fixture(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def _ficha_with_estado(estado: str) -> str:
    payload = json.loads(_fixture("bumeran_activo.json"))
    payload["aviso"]["estado"] = estado
    return json.dumps(payload)


def _response(status_code: int, text: str = "", location: str | None = None):
    response = MagicMock()
    response.status_code = status_code
    response.text = text
    response.headers = {"Location": location} if location else {}
    return response


class ClassifyFichaTest(unittest.TestCase):

    def test_an_active_posting_is_alive(self):
        check = classify_ficha(200, _fixture("bumeran_activo.json"))
        self.assertEqual(check.outcome, LivenessOutcome.ALIVE)

    def test_an_offline_posting_is_closed(self):
        check = classify_ficha(200, _fixture("bumeran_offline.json"))
        self.assertEqual(check.outcome, LivenessOutcome.CLOSED)
        self.assertEqual(check.reason, "estado offline")

    def test_an_expired_posting_is_closed(self):
        self.assertEqual(
            classify_ficha(200, _ficha_with_estado("vencido")).outcome, LivenessOutcome.CLOSED
        )

    def test_an_estado_nobody_has_seen_is_unknown_rather_than_alive(self):
        check = classify_ficha(200, _ficha_with_estado("pausado"))
        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
        self.assertFalse(check.blocked)
        self.assertFalse(check.failed)

    def test_a_missing_posting_is_gone(self):
        self.assertEqual(classify_ficha(404, "").outcome, LivenessOutcome.GONE)
        self.assertEqual(classify_ficha(410, "").outcome, LivenessOutcome.GONE)

    def test_forbidden_and_rate_limited_answers_block_the_source(self):
        for status in (403, 429):
            check = classify_ficha(status, "")
            self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
            self.assertTrue(check.blocked)

    def test_a_challenge_page_blocks_the_source(self):
        check = classify_ficha(200, CHALLENGE)
        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
        self.assertTrue(check.blocked)

    def test_a_server_error_is_an_unknown_failure(self):
        check = classify_ficha(503, "")
        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
        self.assertTrue(check.failed)

    def test_a_body_that_is_not_a_posting_is_unknown(self):
        self.assertEqual(classify_ficha(200, "not json").outcome, LivenessOutcome.UNKNOWN)
        self.assertEqual(classify_ficha(200, "[]").outcome, LivenessOutcome.UNKNOWN)
        self.assertEqual(classify_ficha(200, "{}").outcome, LivenessOutcome.UNKNOWN)

    def test_a_bad_request_is_unknown(self):
        self.assertEqual(classify_ficha(400, "").outcome, LivenessOutcome.UNKNOWN)

    def test_the_posting_id_comes_from_the_end_of_the_url(self):
        self.assertEqual(aviso_id_of(BUMERAN_URL), "1118438425")
        self.assertEqual(aviso_id_of(BUMERAN_URL + "?utm=x"), "1118438425")
        self.assertIsNone(aviso_id_of("https://www.bumeran.com.ar/empleos.html"))


class ClassifyOfferTest(unittest.TestCase):

    def test_a_posting_with_its_apply_button_is_alive(self):
        check = classify_offer_page(_fixture("computrabajo_alive.html"))
        self.assertEqual(check.outcome, LivenessOutcome.ALIVE)

    def test_a_posting_without_the_apply_button_is_unknown(self):
        html = _fixture("computrabajo_alive.html").replace("data-href-offer-apply", "data-x")
        self.assertEqual(classify_offer_page(html).outcome, LivenessOutcome.UNKNOWN)

    def test_a_page_that_is_not_a_posting_is_unknown(self):
        self.assertEqual(
            classify_offer_page("<html><body><h1>Ofertas</h1></body></html>").outcome,
            LivenessOutcome.UNKNOWN,
        )

    def test_status_codes_follow_the_shared_rules(self):
        self.assertEqual(classify_offer_response(404, "").outcome, LivenessOutcome.GONE)
        self.assertTrue(classify_offer_response(429, "").blocked)
        self.assertTrue(classify_offer_response(500, "").failed)
        self.assertEqual(classify_offer_response(204, "").outcome, LivenessOutcome.UNKNOWN)

    def test_only_offer_paths_are_postings(self):
        self.assertTrue(is_offer_url(OFFER_URL))
        self.assertFalse(is_offer_url(LISTING_REDIRECT))
        self.assertFalse(is_offer_url("https://ar.computrabajo.com/"))


class ComputrabajoLivenessCheckerTest(unittest.TestCase):

    def setUp(self):
        self.checker = ComputrabajoLivenessChecker(timeout=1.0)
        self.session = MagicMock()
        self.checker._session = self.session

    def test_a_live_posting_is_alive(self):
        self.session.get.return_value = _response(200, _fixture("computrabajo_alive.html"))

        self.assertEqual(self.checker.check(OFFER_URL).outcome, LivenessOutcome.ALIVE)
        self.assertFalse(self.session.get.call_args.kwargs["allow_redirects"])

    def test_a_redirect_to_the_listing_is_gone_without_fetching_it(self):
        self.session.get.return_value = _response(301, location=LISTING_REDIRECT)

        check = self.checker.check(OFFER_URL)

        self.assertEqual(check.outcome, LivenessOutcome.GONE)
        self.assertIn("/trabajo-de-auxiliar-de-sistemas-en-neuquen", check.reason)
        self.assertEqual(self.session.get.call_count, 1)

    def test_a_redirect_to_the_home_page_is_gone(self):
        self.session.get.return_value = _response(302, location="/")
        self.assertEqual(self.checker.check(OFFER_URL).outcome, LivenessOutcome.GONE)

    def test_a_redirect_to_another_posting_is_followed(self):
        moved = "/ofertas-de-trabajo/oferta-de-trabajo-de-auxiliar-de-sistemas-ABC"
        self.session.get.side_effect = [
            _response(301, location=moved),
            _response(200, _fixture("computrabajo_alive.html")),
        ]

        self.assertEqual(self.checker.check(OFFER_URL).outcome, LivenessOutcome.ALIVE)
        self.assertEqual(
            self.session.get.call_args.args[0], "https://ar.computrabajo.com" + moved
        )

    def test_a_posting_that_keeps_redirecting_is_unknown(self):
        self.session.get.return_value = _response(301, location=OFFER_URL)

        self.assertEqual(self.checker.check(OFFER_URL).outcome, LivenessOutcome.UNKNOWN)
        self.assertEqual(self.session.get.call_count, 3)

    def test_a_timeout_is_an_unknown_failure(self):
        self.session.get.side_effect = requests.Timeout("read timed out")

        check = self.checker.check(OFFER_URL)

        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
        self.assertTrue(check.failed)

    def test_the_session_is_created_once(self):
        checker = ComputrabajoLivenessChecker(timeout=1.0)
        self.assertIs(checker._ensure_session(), checker._ensure_session())


class BumeranLivenessCheckerTest(unittest.TestCase):

    def setUp(self):
        self.checker = BumeranLivenessChecker(timeout=1.0)
        self.session = MagicMock()
        self.checker._session = self.session

    def test_asks_the_posting_api_for_the_id_in_the_url(self):
        self.session.get.return_value = _response(200, _fixture("bumeran_activo.json"))

        check = self.checker.check(BUMERAN_URL)

        self.assertEqual(check.outcome, LivenessOutcome.ALIVE)
        self.assertEqual(self.session.get.call_args.args[0], f"{FICHA_URL}/1118438425")

    def test_a_finished_posting_is_closed(self):
        self.session.get.return_value = _response(200, _fixture("bumeran_offline.json"))
        self.assertEqual(self.checker.check(BUMERAN_URL).outcome, LivenessOutcome.CLOSED)

    def test_a_url_without_a_posting_id_is_unknown_without_a_request(self):
        check = self.checker.check("https://www.bumeran.com.ar/empleos.html")

        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)
        self.session.get.assert_not_called()

    def test_a_connection_error_is_an_unknown_failure(self):
        self.session.get.side_effect = requests.ConnectionError("refused")

        check = self.checker.check(BUMERAN_URL)

        self.assertTrue(check.failed)
        self.assertEqual(check.outcome, LivenessOutcome.UNKNOWN)

    def test_the_session_warms_up_once(self):
        checker = BumeranLivenessChecker(timeout=1.0)
        with patch("scraper.bumeran.warm_up") as warm_up:
            first = checker._ensure_session()
            second = checker._ensure_session()

        self.assertIs(first, second)
        warm_up.assert_called_once()


class GetLivenessCheckersTest(unittest.TestCase):

    def test_builds_one_checker_per_source_keyed_by_lowercase_name(self):
        checkers = get_liveness_checkers([" Bumeran ", "computrabajo"], timeout=4.0)

        self.assertIsInstance(checkers["bumeran"], BumeranLivenessChecker)
        self.assertIsInstance(checkers["computrabajo"], ComputrabajoLivenessChecker)
        self.assertEqual(checkers["bumeran"].timeout, 4.0)

    def test_an_unknown_source_is_rejected(self):
        with self.assertRaises(ValueError):
            get_liveness_checkers(["linkedin"], timeout=4.0)


if __name__ == "__main__":
    unittest.main()
