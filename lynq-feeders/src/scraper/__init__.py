from __future__ import annotations

from scraper.base import LivenessCheck, LivenessChecker, LivenessOutcome, Listing, Scraper
from scraper.bumeran import BumeranLivenessChecker, BumeranScraper
from scraper.computrabajo import ComputrabajoLivenessChecker, ComputrabajoScraper

__all__ = [
    "Listing",
    "Scraper",
    "BumeranScraper",
    "ComputrabajoScraper",
    "get_scrapers",
    "LivenessCheck",
    "LivenessChecker",
    "LivenessOutcome",
    "BumeranLivenessChecker",
    "ComputrabajoLivenessChecker",
    "get_liveness_checkers",
]

_BUILDERS = {
    BumeranScraper.source: BumeranScraper,
    ComputrabajoScraper.source: ComputrabajoScraper,
}

_LIVENESS_BUILDERS = {
    BumeranLivenessChecker.source: BumeranLivenessChecker,
    ComputrabajoLivenessChecker.source: ComputrabajoLivenessChecker,
}


def _source_key(source: str) -> str:
    return source.strip().lower()


def get_scrapers(sources: list[str], timeout: float) -> list[Scraper]:
    scrapers: list[Scraper] = []
    for source in sources:
        builder = _BUILDERS.get(_source_key(source))
        if builder is None:
            raise ValueError(f"Unsupported feeder source: {source!r}")
        scrapers.append(builder(timeout=timeout))
    return scrapers


def get_liveness_checkers(sources: list[str], timeout: float) -> dict[str, LivenessChecker]:
    checkers: dict[str, LivenessChecker] = {}
    for source in sources:
        key = _source_key(source)
        builder = _LIVENESS_BUILDERS.get(key)
        if builder is None:
            raise ValueError(f"Unsupported feeder source: {source!r}")
        checkers[key] = builder(timeout=timeout)
    return checkers
