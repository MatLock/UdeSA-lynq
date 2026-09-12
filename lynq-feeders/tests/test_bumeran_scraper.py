from __future__ import annotations

import unittest
from unittest.mock import MagicMock, patch

from scraper.bumeran import BumeranScraper, _is_challenge, _parse_published_at, _to_listing

_AVISO = {
    "id": 1118437287,
    "titulo": "DevSecOps Senior",
    "detalle": "Referente tecnico en iniciativas de DevSecOps.",
    "empresa": "KPMG",
    "localizacion": "Capital Federal, Buenos Aires",
    "modalidadTrabajo": "Remoto",
    "tipoTrabajo": "Full-time",
    "fechaHoraPublicacion": "10-09-2026 15:50:15",
}


def _response(payload, status_code=200, text="{}"):
    response = MagicMock()
    response.status_code = status_code
    response.text = text
    response.json.return_value = payload
    return response


class ParsePublishedAtTest(unittest.TestCase):

    def test_parses_the_full_timestamp(self):
        self.assertEqual(_parse_published_at("10-09-2026 15:50:15"), 1789055415000)

    def test_parses_the_date_only_form(self):
        self.assertEqual(_parse_published_at("10-09-2026"), 1788998400000)

    def test_returns_none_for_missing_or_unparseable_values(self):
        self.assertIsNone(_parse_published_at(None))
        self.assertIsNone(_parse_published_at(""))
        self.assertIsNone(_parse_published_at("ayer"))


class IsChallengeTest(unittest.TestCase):

    def test_detects_an_html_challenge_page(self):
        self.assertTrue(_is_challenge("  <!DOCTYPE html><html>"))
        self.assertTrue(_is_challenge("Attention Required! | Cloudflare"))

    def test_json_is_not_a_challenge(self):
        self.assertFalse(_is_challenge('{"content": []}'))


class ToListingTest(unittest.TestCase):

    def test_maps_every_field_the_api_exposes(self):
        listing = _to_listing(_AVISO, "TECNOLOGIA")
        self.assertEqual(listing.external_id, "1118437287")
        self.assertEqual(listing.title, "DevSecOps Senior")
        self.assertEqual(listing.company, "KPMG")
        self.assertEqual(listing.source, "bumeran")
        self.assertEqual(listing.rubro, "TECNOLOGIA")
        self.assertTrue(listing.remote)
        self.assertEqual(listing.work_type, "Full-time")
        self.assertIn("devsecops-senior-1118437287", listing.apply_url)

    def test_non_remote_modality_is_not_flagged_remote(self):
        listing = _to_listing({**_AVISO, "modalidadTrabajo": "Presencial"}, "TECNOLOGIA")
        self.assertFalse(listing.remote)

    def test_listing_without_a_title_is_dropped(self):
        self.assertIsNone(_to_listing({**_AVISO, "titulo": None}, "TECNOLOGIA"))

    def test_listing_without_an_id_is_dropped(self):
        self.assertIsNone(_to_listing({**_AVISO, "id": None}, "TECNOLOGIA"))


class FetchTest(unittest.TestCase):

    def setUp(self):
        self.scraper = BumeranScraper(timeout=1.0)
        self.session = MagicMock()
        self.scraper._session = self.session

    def test_sends_the_area_filter_and_returns_listings(self):
        self.session.post.return_value = _response({"content": [_AVISO]})

        found = self.scraper.fetch("TECNOLOGIA", 10)

        self.assertEqual(len(found), 1)
        body = self.session.post.call_args.kwargs["json"]
        self.assertEqual(
            body["filtros"], [{"id": "area", "value": "tecnologia-sistemas-y-telecomunicaciones"}]
        )
        self.assertNotIn("query", body)
        self.assertIn("sort=RECIENTES", self.session.post.call_args.args[0])

    def test_sends_a_query_for_rubros_that_share_an_area(self):
        self.session.post.return_value = _response({"content": []})

        self.scraper.fetch("CONTABILIDAD", 10)

        body = self.session.post.call_args.kwargs["json"]
        self.assertEqual(body["query"], "contabilidad")
        self.assertEqual(
            body["filtros"], [{"id": "area", "value": "administracion-contabilidad-y-finanzas"}]
        )

    def test_caps_the_result_at_the_requested_limit(self):
        avisos = [{**_AVISO, "id": index} for index in range(25)]
        self.session.post.return_value = _response({"content": avisos})

        self.assertEqual(len(self.scraper.fetch("TECNOLOGIA", 10)), 10)

    def test_empty_content_yields_no_listings(self):
        self.session.post.return_value = _response({"content": None})
        self.assertEqual(self.scraper.fetch("TECNOLOGIA", 10), [])

    @patch("scraper.bumeran.time.sleep")
    def test_retries_then_gives_up_on_a_persistent_challenge(self, _sleep):
        self.session.post.return_value = _response({}, status_code=403, text="<!DOCTYPE html>")

        with self.assertRaises(RuntimeError):
            self.scraper.fetch("TECNOLOGIA", 10)

        self.assertEqual(self.session.post.call_count, 5)

    @patch("scraper.bumeran.time.sleep")
    def test_recovers_when_a_retry_succeeds(self, _sleep):
        self.session.post.side_effect = [
            _response({}, status_code=403, text="<!DOCTYPE html>"),
            _response({"content": [_AVISO]}),
        ]

        found = self.scraper.fetch("TECNOLOGIA", 10)

        self.assertEqual(len(found), 1)
        self.assertEqual(self.session.post.call_count, 2)


if __name__ == "__main__":
    unittest.main()
