from __future__ import annotations


def bare_language(code: str) -> str:
    return code.split("-")[0].split("_")[0].strip().lower()
