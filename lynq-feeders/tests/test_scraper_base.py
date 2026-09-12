from __future__ import annotations

import unittest

from scraper.base import Listing, slugify, sort_latest_first, strip_accents


def _listing(external_id: str, posted_at: int | None) -> Listing:
    return Listing(
        external_id=external_id,
        title="Title",
        source="bumeran",
        rubro="TECNOLOGIA",
        posted_at=posted_at,
    )


class SlugifyTest(unittest.TestCase):

    def test_lowercases_and_hyphenates(self):
        self.assertEqual(slugify("Desarrollador Python"), "desarrollador-python")

    def test_drops_accents_and_punctuation(self):
        self.assertEqual(slugify("Administración, Contabilidad"), "administracion-contabilidad")

    def test_collapses_repeated_separators(self):
        self.assertEqual(slugify("QA   --  Senior"), "qa-senior")


class StripAccentsTest(unittest.TestCase):

    def test_normalizes_to_lowercase_ascii(self):
        self.assertEqual(strip_accents("Hace 3 DÍAS"), "hace 3 dias")


class SortLatestFirstTest(unittest.TestCase):

    def test_orders_newest_first(self):
        ordered = sort_latest_first([_listing("a", 100), _listing("b", 300), _listing("c", 200)])
        self.assertEqual([item.external_id for item in ordered], ["b", "c", "a"])

    def test_listings_without_a_date_sink_to_the_bottom(self):
        ordered = sort_latest_first([_listing("a", None), _listing("b", 1)])
        self.assertEqual([item.external_id for item in ordered], ["b", "a"])


class ListingDefaultsTest(unittest.TestCase):

    def test_skills_and_tags_default_to_empty_lists(self):
        listing = _listing("a", 1)
        self.assertEqual(listing.skills, [])
        self.assertEqual(listing.similarity_tags, [])
        self.assertFalse(listing.remote)


if __name__ == "__main__":
    unittest.main()
