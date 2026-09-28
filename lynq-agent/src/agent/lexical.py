from __future__ import annotations

import string
import unicodedata

# Names that spell the same technology. Membership is decided here, in a table a
# person maintains, never by prefix: `Java` is not `JavaScript` and `React` does
# not authorise `React Native`. Every member is written in its normalized form.
ALIAS_GROUPS: tuple[frozenset[str], ...] = (
    frozenset({"postgres", "postgresql", "psql"}),
    frozenset({"js", "javascript"}),
    frozenset({"ts", "typescript"}),
    frozenset({"k8s", "kubernetes"}),
    frozenset({"golang", "go"}),
    frozenset({"py", "python"}),
    frozenset({"c#", "csharp"}),
    frozenset({"c++", "cpp"}),
    frozenset({"mongo", "mongodb"}),
    frozenset({"aws", "amazonwebservices"}),
    frozenset({"gcp", "googlecloud", "googlecloudplatform"}),
    frozenset({"azure", "microsoftazure"}),
    frozenset({"tf", "terraform"}),
    frozenset({"dotnet", "net", "netcore"}),
    frozenset({"node", "nodejs"}),
    frozenset({"react", "reactjs"}),
    frozenset({"vue", "vuejs"}),
    frozenset({"sklearn", "scikitlearn"}),
    frozenset({"tailwind", "tailwindcss"}),
    frozenset({"rest", "restapi", "restful"}),
    frozenset({"ghactions", "githubactions"}),
    frozenset({"mssql", "sqlserver", "microsoftsqlserver"}),
    frozenset({"elastic", "elasticsearch"}),
    frozenset({"rabbit", "rabbitmq"}),
    frozenset({"qa", "qualityassurance"}),
)

# `C#` and `C++` would collapse into `C` without these.
_KEPT = frozenset("+#")
_STRIPPED = "".join(
    character
    for character in string.punctuation + "«»¿¡“”’–—"
    if character not in _KEPT
)
# A claim this short (`Go`, `R`, `C`) is also an ordinary word, so it only
# matches a token spelled exactly as the claim is.
SHORT_CLAIM_CHARS = 2
MAX_CLAIM_WORDS = 6

_ALIASES: dict[str, frozenset[str]] = {
    member: group for group in ALIAS_GROUPS for member in group
}


def normalize(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text)
    folded = "".join(
        character for character in decomposed if not unicodedata.combining(character)
    ).lower()
    return "".join(
        character for character in folded if character.isalnum() or character in _KEPT
    )


def forms_of(normalized: str) -> frozenset[str]:
    if not normalized:
        return frozenset()
    return _ALIASES.get(normalized, frozenset({normalized}))


def same_skill(left: str, right: str) -> bool:
    return normalize(right) in forms_of(normalize(left))


def words(text: str) -> list[str]:
    return [
        word.strip(_STRIPPED) for word in text.split() if word.strip(_STRIPPED)
    ]


def find_match(text: str, claim: str) -> str | None:
    """The wording `text` uses for `claim`, or None when it does not name it."""
    wanted = normalize(claim)
    if not wanted:
        return None
    forms = forms_of(wanted)
    exact = claim.strip() if len(wanted) <= SHORT_CLAIM_CHARS else None

    tokens = [(word, normalize(word)) for word in words(text)]
    tokens = [(word, normalized) for word, normalized in tokens if normalized]
    for width in range(1, min(MAX_CLAIM_WORDS, len(tokens)) + 1):
        for start in range(len(tokens) - width + 1):
            window = tokens[start : start + width]
            if "".join(normalized for _, normalized in window) not in forms:
                continue
            if exact is not None and (width != 1 or window[0][0] != exact):
                continue
            return " ".join(word for word, _ in window)
    return None
