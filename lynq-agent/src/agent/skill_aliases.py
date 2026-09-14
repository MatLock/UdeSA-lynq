from __future__ import annotations

import unicodedata

ALIAS_GROUPS: tuple[frozenset[str], ...] = (
    frozenset({"postgres", "postgresql", "psql"}),
    frozenset({"js", "javascript", "ecmascript"}),
    frozenset({"ts", "typescript"}),
    frozenset({"k8s", "kubernetes"}),
    frozenset({"golang", "go"}),
    frozenset({"py", "python"}),
    frozenset({"cs", "c#", "csharp", "c sharp"}),
    frozenset({"cpp", "c++"}),
    frozenset({"postgis", "post gis"}),
    frozenset({"mysql", "my sql"}),
    frozenset({"mssql", "sql server", "microsoft sql server"}),
    frozenset({"mongo", "mongodb"}),
    frozenset({"elastic", "elasticsearch", "elastic search"}),
    frozenset({"rabbit", "rabbitmq"}),
    frozenset({"aws", "amazon web services"}),
    frozenset({"gcp", "google cloud", "google cloud platform"}),
    frozenset({"azure", "microsoft azure"}),
    frozenset({"ci/cd", "cicd", "ci cd"}),
    frozenset({"github actions", "gh actions"}),
    frozenset({"gitlab ci", "gitlab-ci"}),
    frozenset({"tf", "terraform"}),
    frozenset({"k6", "grafana k6"}),
    frozenset({"postman", "postman api"}),
    frozenset({"rest", "rest api", "restful"}),
    frozenset({"graphql", "graph ql"}),
    frozenset({"springboot", "spring boot"}),
    frozenset({"dotnet", ".net", "net core", ".net core"}),
    frozenset({"nodejs", "node", "node.js"}),
    frozenset({"reactjs", "react", "react.js"}),
    frozenset({"vuejs", "vue", "vue.js"}),
    frozenset({"angularjs", "angular"}),
    frozenset({"sass", "scss"}),
    frozenset({"tailwind", "tailwindcss", "tailwind css"}),
    frozenset({"pandas", "python pandas"}),
    frozenset({"sklearn", "scikit-learn", "scikit learn"}),
    frozenset({"tf keras", "keras"}),
    frozenset({"powerbi", "power bi"}),
    frozenset({"excel", "microsoft excel", "ms excel"}),
    frozenset({"sap", "sap erp"}),
    frozenset({"qa", "quality assurance", "aseguramiento de calidad"}),
    frozenset({"rrhh", "recursos humanos", "hr", "human resources"}),
    frozenset({"scrum master", "scrum"}),
    frozenset({"contabilidad", "accounting"}),
    frozenset({"facturacion", "billing"}),
)


def normalize(name: str) -> str:
    folded = unicodedata.normalize("NFKD", name.strip().lower())
    without_accents = "".join(c for c in folded if not unicodedata.combining(c))
    return " ".join(without_accents.split())


_BY_MEMBER: dict[str, frozenset[str]] = {}
for _group in ALIAS_GROUPS:
    for _member in _group:
        _BY_MEMBER[normalize(_member)] = _group


def aliases_of(name: str) -> frozenset[str]:
    key = normalize(name)
    group = _BY_MEMBER.get(key)
    if group is None:
        return frozenset({key})
    return frozenset(normalize(member) for member in group)


def are_same_technology(left: str, right: str) -> bool:
    left_key = normalize(left)
    right_key = normalize(right)
    if left_key == right_key:
        return True
    return right_key in aliases_of(left_key)
