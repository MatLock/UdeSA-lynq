from __future__ import annotations

import unittest

from scraper.rubros import (
    ADMINISTRACION,
    CONTABILIDAD,
    KNOWN_RUBROS,
    RECURSOS_HUMANOS,
    TECNOLOGIA,
    bumeran_rubro,
    computrabajo_rubro,
    keyword_for,
)


class BumeranRubroTest(unittest.TestCase):

    def test_every_known_rubro_maps_to_a_real_area(self):
        for rubro in KNOWN_RUBROS:
            self.assertIsNotNone(bumeran_rubro(rubro).area, rubro)

    def test_tecnologia_maps_to_the_technology_area_without_a_query(self):
        config = bumeran_rubro(TECNOLOGIA)
        self.assertEqual(config.area, "tecnologia-sistemas-y-telecomunicaciones")
        self.assertEqual(config.query, "")

    def test_administracion_and_contabilidad_share_an_area_but_differ_by_query(self):
        administracion = bumeran_rubro(ADMINISTRACION)
        contabilidad = bumeran_rubro(CONTABILIDAD)
        self.assertEqual(administracion.area, contabilidad.area)
        self.assertNotEqual(administracion.query, contabilidad.query)
        self.assertTrue(administracion.query)
        self.assertTrue(contabilidad.query)

    def test_unknown_rubro_falls_back_to_a_keyword_query(self):
        config = bumeran_rubro("LOGISTICA")
        self.assertIsNone(config.area)
        self.assertEqual(config.query, "logistica")


class ComputrabajoRubroTest(unittest.TestCase):

    def test_every_known_rubro_maps_to_a_slug(self):
        for rubro in KNOWN_RUBROS:
            self.assertTrue(computrabajo_rubro(rubro).slug, rubro)

    def test_tecnologia_uses_the_sistemas_slug(self):
        self.assertEqual(computrabajo_rubro(TECNOLOGIA).slug, "sistemas")

    def test_recursos_humanos_slug_is_hyphenated(self):
        self.assertEqual(computrabajo_rubro(RECURSOS_HUMANOS).slug, "recursos-humanos")

    def test_unknown_rubro_falls_back_to_a_hyphenated_keyword(self):
        self.assertEqual(computrabajo_rubro("COMERCIO_EXTERIOR").slug, "comercio-exterior")


class KeywordForTest(unittest.TestCase):

    def test_unknown_rubro_becomes_lowercase_words(self):
        self.assertEqual(keyword_for("ATENCION_AL_CLIENTE"), "atencion al cliente")


if __name__ == "__main__":
    unittest.main()
