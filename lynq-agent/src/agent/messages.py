from __future__ import annotations

PROTOCOL_LANGUAGE = "en"
DEFAULT_LANGUAGE = "es"

CATALOG: dict[str, dict[str, str]] = {
    "immutable_section": {
        "en": (
            "{section} is immutable: the candidate's name, email, phone and "
            "links cannot be edited"
        ),
        "es": (
            "{section} es inmutable: no se puede editar el nombre, el mail, "
            "el telefono ni los links del candidato"
        ),
    },
    "unknown_section": {
        "en": "unknown section: {section}",
        "es": "seccion desconocida: {section}",
    },
    "unsupported_op": {
        "en": "unsupported operation for {section}: {op}",
        "es": "operacion no soportada para {section}: {op}",
    },
    "missing_summary_text": {
        "en": "the summary text is missing",
        "es": "falta el texto del summary",
    },
    "unknown_skill_bucket": {
        "en": "unknown skills bucket: {bucket}",
        "es": "bucket de skills desconocido: {bucket}",
    },
    "missing_skill_name": {
        "en": "the skill name is missing",
        "es": "falta el nombre de la skill",
    },
    "skill_already_present": {
        "en": "{name} is already in skills.{bucket}",
        "es": "{name} ya esta en skills.{bucket}",
    },
    "skill_not_present": {
        "en": "{name} is not in skills.{bucket}",
        "es": "{name} no esta en skills.{bucket}",
    },
    "unevidenced_skill": {
        "en": (
            "no evidence of {name} in the base resume: a skill the candidate "
            "cannot back is not added"
        ),
        "es": (
            "no hay evidencia de {name} en el CV base: no se agrega una skill "
            "que el candidato no pueda respaldar"
        ),
    },
    "unevidenced_technology": {
        "en": "no evidence of {name} in the base resume",
        "es": "no hay evidencia de {name} en el CV base",
    },
    "skills_order_not_a_permutation": {
        "en": "order must be a permutation of the {size} items in skills.{bucket}",
        "es": (
            "order tiene que ser una permutacion de los {size} elementos de "
            "skills.{bucket}"
        ),
    },
    "entries_order_not_a_permutation": {
        "en": (
            "order must be a permutation of the {size} entries of {section}: "
            "entries are neither added nor removed"
        ),
        "es": (
            "order tiene que ser una permutacion de las {size} entradas de "
            "{section}: no se agregan ni se borran entradas"
        ),
    },
    "index_out_of_range": {
        "en": "index out of range for {section}: there are {size} entries",
        "es": "index fuera de rango para {section}: hay {size} entradas",
    },
    "immutable_entry_fields": {
        "en": "the hard facts of a {section} entry cannot be changed: {fields}",
        "es": (
            "no se pueden cambiar los datos duros de una entrada de {section}: "
            "{fields}"
        ),
    },
    "non_editable_fields": {
        "en": "fields that cannot be edited in {section}: {fields}",
        "es": "campos no editables en {section}: {fields}",
    },
    "nothing_to_rewrite": {
        "en": "there is nothing to rewrite",
        "es": "no hay nada que reescribir",
    },
    "summary_rewritten": {
        "en": "summary rewritten",
        "es": "summary reescrito",
    },
    "skill_added": {
        "en": "{name} added to skills.{bucket}",
        "es": "{name} agregada a skills.{bucket}",
    },
    "skill_removed": {
        "en": "{name} removed from skills.{bucket}",
        "es": "{name} quitada de skills.{bucket}",
    },
    "skills_reordered": {
        "en": "skills.{bucket} reordered",
        "es": "skills.{bucket} reordenadas",
    },
    "section_reordered": {
        "en": "{section} reordered",
        "es": "{section} reordenado",
    },
    "section_rewritten": {
        "en": "{section} rewritten in {label}",
        "es": "{section} reescrito en {label}",
    },
}


def render(key: str, language: str, **params) -> str:
    variants = CATALOG[key]
    template = variants.get(language) or variants[DEFAULT_LANGUAGE]
    return template.format(**params)
