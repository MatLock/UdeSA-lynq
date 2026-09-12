from __future__ import annotations

from typing import Optional

from pydantic import BaseModel

ADMINISTRACION = "ADMINISTRACION"
TECNOLOGIA = "TECNOLOGIA"
CONTABILIDAD = "CONTABILIDAD"
RECURSOS_HUMANOS = "RECURSOS_HUMANOS"

BUMERAN = "bumeran"
COMPUTRABAJO = "computrabajo"


class BumeranRubro(BaseModel):
    area: Optional[str] = None
    query: str = ""


class ComputrabajoRubro(BaseModel):
    slug: str


BUMERAN_RUBROS: dict[str, BumeranRubro] = {
    ADMINISTRACION: BumeranRubro(
        area="administracion-contabilidad-y-finanzas", query="administracion"
    ),
    TECNOLOGIA: BumeranRubro(area="tecnologia-sistemas-y-telecomunicaciones"),
    CONTABILIDAD: BumeranRubro(
        area="administracion-contabilidad-y-finanzas", query="contabilidad"
    ),
    RECURSOS_HUMANOS: BumeranRubro(area="recursos-humanos-y-capacitacion"),
}

COMPUTRABAJO_RUBROS: dict[str, ComputrabajoRubro] = {
    ADMINISTRACION: ComputrabajoRubro(slug="administracion"),
    TECNOLOGIA: ComputrabajoRubro(slug="sistemas"),
    CONTABILIDAD: ComputrabajoRubro(slug="contabilidad"),
    RECURSOS_HUMANOS: ComputrabajoRubro(slug="recursos-humanos"),
}

KEYWORD_FALLBACK: dict[str, str] = {
    ADMINISTRACION: "administracion",
    TECNOLOGIA: "sistemas",
    CONTABILIDAD: "contabilidad",
    RECURSOS_HUMANOS: "recursos humanos",
}

KNOWN_RUBROS = tuple(KEYWORD_FALLBACK)


def keyword_for(rubro: str) -> str:
    return KEYWORD_FALLBACK.get(rubro, rubro.replace("_", " ").lower())


def bumeran_rubro(rubro: str) -> BumeranRubro:
    known = BUMERAN_RUBROS.get(rubro)
    if known is not None:
        return known
    return BumeranRubro(query=keyword_for(rubro))


def computrabajo_rubro(rubro: str) -> ComputrabajoRubro:
    known = COMPUTRABAJO_RUBROS.get(rubro)
    if known is not None:
        return known
    return ComputrabajoRubro(slug=keyword_for(rubro).replace(" ", "-"))
