from __future__ import annotations

from scraper.base import Listing, Scraper
from scraper.bumeran import BumeranScraper
from scraper.computrabajo import ComputrabajoScraper

__all__ = ["Listing", "Scraper", "BumeranScraper", "ComputrabajoScraper", "get_scrapers"]

_BUILDERS = {
    BumeranScraper.source: BumeranScraper,
    ComputrabajoScraper.source: ComputrabajoScraper,
}


def get_scrapers(sources: list[str], timeout: float) -> list[Scraper]:
    scrapers: list[Scraper] = []
    for source in sources:
        builder = _BUILDERS.get(source.strip().lower())
        if builder is None:
            raise ValueError(f"Unsupported feeder source: {source!r}")
        scrapers.append(builder(timeout=timeout))
    return scrapers
