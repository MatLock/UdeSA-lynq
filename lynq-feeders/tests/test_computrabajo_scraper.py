from __future__ import annotations

import unittest
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

from bs4 import BeautifulSoup

from scraper.computrabajo import (
    ComputrabajoScraper,
    parse_card,
    parse_relative_date,
    parse_salary,
)

NOW = datetime(2026, 9, 12, 12, 0, 0, tzinfo=timezone.utc)

CARD_HTML = """
<article class="box_offer" data-id="01C717D17245C0A161373E686DCF3405">
  <h2 class="fs18 fwB prB">
    <a class="js-o-link fc_base" href="/ofertas-de-trabajo/oferta-de-ref-1054-backend-01C717#lc=ListOffers">
      REF 1054: Desarrollador Backend
    </a>
  </h2>
  <p class="dFlex vm_fx fs16 fc_base mt5">
    <a class="fc_base t_ellipsis" href="https://ar.computrabajo.com/empresas/lectus">
      Lectus - Soluciones en RRHH
    </a>
  </p>
  <p class="fs16 fc_base mt5"><span class="mr10">C&oacute;rdoba, C&oacute;rdoba</span></p>
  <p class="fs13 fc_aux mt15">Hace  2  horas</p>
  <div class="fs13"><span>Remoto</span></div>
  <span>$ 1.500.000 a $ 2.000.000</span>
</article>
"""

DETAIL_HTML = """
<html><body>
  <div description-offer="true">
    <div class="mb40 pb40 bb1">
      Descripci&oacute;n de la oferta
      Buscamos un desarrollador con 5 a&ntilde;os de experiencia en Python.
      Trabajo en equipo y autonom&iacute;a.
      Aptitudes asociadas
      Python
    </div>
  </div>
</body></html>
"""


def _card(html=CARD_HTML):
    return BeautifulSoup(html, "lxml").select_one("article.box_offer")


class ParseRelativeDateTest(unittest.TestCase):

    def test_hours_ago(self):
        self.assertEqual(parse_relative_date("Hace 2 horas", NOW), 1789207200000)

    def test_double_spaced_hours_still_parse(self):
        self.assertEqual(parse_relative_date("Hace  2  horas", NOW), 1789207200000)

    def test_yesterday(self):
        self.assertEqual(parse_relative_date("Ayer", NOW), 1789128000000)

    def test_days_weeks_and_months(self):
        self.assertLess(parse_relative_date("Hace 3 dias", NOW), parse_relative_date("Ayer", NOW))
        self.assertLess(
            parse_relative_date("Hace 2 semanas", NOW), parse_relative_date("Hace 3 dias", NOW)
        )
        self.assertLess(
            parse_relative_date("Hace 2 meses", NOW), parse_relative_date("Hace 2 semanas", NOW)
        )

    def test_accented_input_is_handled(self):
        self.assertIsNotNone(parse_relative_date("Hace 3 días", NOW))

    def test_unknown_or_missing_label(self):
        self.assertIsNone(parse_relative_date(None, NOW))
        self.assertIsNone(parse_relative_date("Publicado recientemente", NOW))


class ParseSalaryTest(unittest.TestCase):

    def test_range_yields_min_max_and_currency(self):
        self.assertEqual(parse_salary("$ 1.500.000 a $ 2.000.000"), (1500000.0, 2000000.0, "ARS"))

    def test_single_amount_sets_only_the_minimum(self):
        self.assertEqual(parse_salary("$ 900.000"), (900000.0, None, "ARS"))

    def test_missing_or_textual_salary(self):
        self.assertEqual(parse_salary(None), (None, None, None))
        self.assertEqual(parse_salary("A convenir"), (None, None, None))


class ParseCardTest(unittest.TestCase):

    def test_maps_the_card_fields(self):
        listing = parse_card(_card(), "TECNOLOGIA", NOW)

        self.assertEqual(listing.external_id, "01C717D17245C0A161373E686DCF3405")
        self.assertEqual(listing.title, "REF 1054: Desarrollador Backend")
        self.assertEqual(listing.company, "Lectus - Soluciones en RRHH")
        self.assertEqual(listing.location, "Córdoba, Córdoba")
        self.assertEqual(listing.source, "computrabajo")
        self.assertEqual(listing.rubro, "TECNOLOGIA")
        self.assertTrue(listing.remote)
        self.assertEqual(listing.salary_min, 1500000.0)
        self.assertEqual(listing.currency, "ARS")

    def test_apply_url_is_absolute_and_loses_the_fragment(self):
        listing = parse_card(_card(), "TECNOLOGIA", NOW)
        self.assertTrue(listing.apply_url.startswith("https://ar.computrabajo.com/"))
        self.assertNotIn("#", listing.apply_url)

    def test_card_without_a_title_is_dropped(self):
        html = CARD_HTML.replace('class="js-o-link fc_base"', 'class="other"')
        self.assertIsNone(parse_card(_card(html), "TECNOLOGIA", NOW))

    def test_card_without_a_data_id_is_dropped(self):
        html = CARD_HTML.replace('data-id="01C717D17245C0A161373E686DCF3405"', "")
        self.assertIsNone(parse_card(_card(html), "TECNOLOGIA", NOW))


class FetchTest(unittest.TestCase):

    def setUp(self):
        self.scraper = ComputrabajoScraper(timeout=1.0)
        self.sleep_patch = patch("scraper.computrabajo.time.sleep")
        self.sleep_patch.start()
        self.addCleanup(self.sleep_patch.stop)

    def _listing_page(self, cards=1):
        return f"<html><body>{CARD_HTML * cards}</body></html>"

    def test_uses_the_rubro_slug_and_enriches_from_the_detail_page(self):
        self.scraper._get = MagicMock(side_effect=[self._listing_page(), DETAIL_HTML])

        found = self.scraper.fetch("TECNOLOGIA", 10)

        self.assertEqual(len(found), 1)
        self.assertIn("/trabajo-de-sistemas", self.scraper._get.call_args_list[0].args[0])
        self.assertIn("desarrollador", found[0].description.lower())
        self.assertNotIn("Aptitudes asociadas", found[0].description)
        self.assertEqual(found[0].experience_level, "5 años de experiencia")

    def test_caps_the_result_at_the_requested_limit(self):
        self.scraper._get = MagicMock(
            side_effect=[self._listing_page(cards=5)] + [DETAIL_HTML] * 5
        )

        self.assertEqual(len(self.scraper.fetch("TECNOLOGIA", 2)), 2)

    def test_a_failing_detail_page_does_not_drop_the_listing(self):
        self.scraper._get = MagicMock(
            side_effect=[self._listing_page(), RuntimeError("403 forever")]
        )

        found = self.scraper.fetch("TECNOLOGIA", 10)

        self.assertEqual(len(found), 1)
        self.assertIsNone(found[0].description)

    def test_a_page_without_cards_yields_nothing(self):
        self.scraper._get = MagicMock(return_value="<html><body></body></html>")
        self.assertEqual(self.scraper.fetch("TECNOLOGIA", 10), [])


if __name__ == "__main__":
    unittest.main()
