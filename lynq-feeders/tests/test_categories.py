from __future__ import annotations

import unittest

from scraper.categories import (
    ADMINISTRACION,
    CONTABILIDAD,
    KNOWN_CATEGORIES,
    RECURSOS_HUMANOS,
    TECNOLOGIA,
    bumeran_category,
    computrabajo_category,
    keyword_for,
)


class BumeranCategoryTest(unittest.TestCase):

    def test_every_known_category_maps_to_a_real_area(self):
        for category in KNOWN_CATEGORIES:
            self.assertIsNotNone(bumeran_category(category).area, category)

    def test_tecnologia_maps_to_the_technology_area_without_a_query(self):
        config = bumeran_category(TECNOLOGIA)
        self.assertEqual(config.area, "tecnologia-sistemas-y-telecomunicaciones")
        self.assertEqual(config.query, "")

    def test_administracion_and_contabilidad_share_an_area_but_differ_by_query(self):
        administracion = bumeran_category(ADMINISTRACION)
        contabilidad = bumeran_category(CONTABILIDAD)
        self.assertEqual(administracion.area, contabilidad.area)
        self.assertNotEqual(administracion.query, contabilidad.query)
        self.assertTrue(administracion.query)
        self.assertTrue(contabilidad.query)

    def test_unknown_category_falls_back_to_a_keyword_query(self):
        config = bumeran_category("LOGISTICA")
        self.assertIsNone(config.area)
        self.assertEqual(config.query, "logistica")


class ComputrabajoCategoryTest(unittest.TestCase):

    def test_every_known_category_maps_to_a_slug(self):
        for category in KNOWN_CATEGORIES:
            self.assertTrue(computrabajo_category(category).slug, category)

    def test_tecnologia_uses_the_sistemas_slug(self):
        self.assertEqual(computrabajo_category(TECNOLOGIA).slug, "sistemas")

    def test_recursos_humanos_slug_is_hyphenated(self):
        self.assertEqual(computrabajo_category(RECURSOS_HUMANOS).slug, "recursos-humanos")

    def test_unknown_category_falls_back_to_a_hyphenated_keyword(self):
        self.assertEqual(computrabajo_category("COMERCIO_EXTERIOR").slug, "comercio-exterior")


class KeywordForTest(unittest.TestCase):

    def test_unknown_category_becomes_lowercase_words(self):
        self.assertEqual(keyword_for("ATENCION_AL_CLIENTE"), "atencion al cliente")


if __name__ == "__main__":
    unittest.main()
