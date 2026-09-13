from __future__ import annotations

from typing import Optional

from pydantic import BaseModel

ADMINISTRACION = "ADMINISTRACION"
TECNOLOGIA = "TECNOLOGIA"
CONTABILIDAD = "CONTABILIDAD"
RECURSOS_HUMANOS = "RECURSOS_HUMANOS"

BUMERAN = "bumeran"
COMPUTRABAJO = "computrabajo"


class BumeranCategory(BaseModel):
    area: Optional[str] = None
    query: str = ""


class ComputrabajoCategory(BaseModel):
    slug: str


BUMERAN_CATEGORIES: dict[str, BumeranCategory] = {
    ADMINISTRACION: BumeranCategory(
        area="administracion-contabilidad-y-finanzas", query="administracion"
    ),
    TECNOLOGIA: BumeranCategory(area="tecnologia-sistemas-y-telecomunicaciones"),
    CONTABILIDAD: BumeranCategory(
        area="administracion-contabilidad-y-finanzas", query="contabilidad"
    ),
    RECURSOS_HUMANOS: BumeranCategory(area="recursos-humanos-y-capacitacion"),
}

COMPUTRABAJO_CATEGORIES: dict[str, ComputrabajoCategory] = {
    ADMINISTRACION: ComputrabajoCategory(slug="administracion"),
    TECNOLOGIA: ComputrabajoCategory(slug="sistemas"),
    CONTABILIDAD: ComputrabajoCategory(slug="contabilidad"),
    RECURSOS_HUMANOS: ComputrabajoCategory(slug="recursos-humanos"),
}

KEYWORD_FALLBACK: dict[str, str] = {
    ADMINISTRACION: "administracion",
    TECNOLOGIA: "sistemas",
    CONTABILIDAD: "contabilidad",
    RECURSOS_HUMANOS: "recursos humanos",
}

KNOWN_CATEGORIES = tuple(KEYWORD_FALLBACK)


def keyword_for(category: str) -> str:
    return KEYWORD_FALLBACK.get(category, category.replace("_", " ").lower())


def bumeran_category(category: str) -> BumeranCategory:
    known = BUMERAN_CATEGORIES.get(category)
    if known is not None:
        return known
    return BumeranCategory(query=keyword_for(category))


def computrabajo_category(category: str) -> ComputrabajoCategory:
    known = COMPUTRABAJO_CATEGORIES.get(category)
    if known is not None:
        return known
    return ComputrabajoCategory(slug=keyword_for(category).replace(" ", "-"))
