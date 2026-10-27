from __future__ import annotations

import json
import random
from collections import Counter, defaultdict
from decimal import Decimal
from statistics import mean
from typing import Any, Callable

from agent.context import Intent
from evaluation.annotation import KINDS, VALID, Label
from evaluation.arms import ARMS
from evaluation.diff import FAULTS

Records = list[dict[str, Any]]
Labels = dict[str, Label]


def _by_arm(records: Records) -> dict[str, Records]:
    arms: dict[str, Records] = defaultdict(list)
    for record in records:
        arms[record["arm"]].append(record)
    return {arm: arms[arm] for arm in ARMS if arm in arms}


def _conversations(records: Records) -> dict[tuple[str, str], Records]:
    groups: dict[tuple[str, str], Records] = defaultdict(list)
    for record in records:
        groups[(record["pair"], record["script"])].append(record)
    return {key: sorted(turns, key=lambda r: r["turn"]) for key, turns in groups.items()}


def _mean(values: list[float]) -> float | None:
    return mean(values) if values else None


def gap_coverage(turns: Records) -> float | None:
    """How much of the posting's unbacked skills the conversation closed: the gaps
    at the start against the gaps at the end, read from the resume alone. A
    design that refuses everything scores zero here; a design that writes
    whatever is asked scores high here and badly on the labels, and the two
    numbers are meant to be read together."""
    done = [t for t in turns if not t.get("error")]
    if not done:
        return None
    start = len(done[0].get("gaps_before") or [])
    end = len(done[-1].get("gaps_after") or [])
    if start == 0:
        return None
    return (start - end) / start


def summarize_arm(records: Records, labels: Labels | None = None) -> dict[str, Any]:
    done = [r for r in records if not r.get("error")]
    edit_turns = [r for r in done if r.get("intent") == Intent.EDIT]
    applied = [u for r in done for u in r.get("units", [])]
    rejected = [u for r in done for u in r.get("rejected", [])]
    faults = Counter(f for r in done for f in r.get("faults", []))
    conversations = _conversations(records)
    coverage = [c for c in (gap_coverage(t) for t in conversations.values()) if c is not None]

    summary: dict[str, Any] = {
        "conversations": len(conversations),
        "turns": len(records),
        "failed_turns": len(records) - len(done),
        "edit_turns": len(edit_turns),
        "turns_with_changes": len([r for r in done if r.get("changes")]),
        "applied_units": len(applied),
        "rejected_units": len(rejected),
        "rejected_by_kind": dict(Counter(u.get("system_kind", "") for u in rejected)),
        "faulty_turns": len([r for r in done if r.get("faults")]),
        "faults_by_kind": {kind: faults.get(kind, 0) for kind in FAULTS if faults.get(kind)},
        "gap_coverage_mean": _mean(coverage),
        "model_calls_per_turn": _mean([r.get("model_calls", 0) for r in done]),
        "tool_calls_per_turn": _mean([r.get("tool_calls", 0) for r in done]),
        "tool_rejections": sum(r.get("tool_rejections", 0) for r in done),
        "passes_per_edit_turn": _mean([r.get("passes", 0) for r in edit_turns]),
        "cost_usd_per_turn": _mean([float(Decimal(r.get("cost_usd") or "0")) for r in done]),
        "latency_ms_per_turn": _mean([r.get("latency_ms", 0) for r in done]),
        "wall_ms_per_turn": _mean([r.get("wall_ms", 0) for r in done]),
    }
    if labels is not None:
        summary.update(labelled_summary(done, labels))
    return summary


def labelled_summary(done: Records, labels: Labels) -> dict[str, Any]:
    applied = [(r, u) for r in done for u in r.get("units", []) if u["id"] in labels]
    rejected = [(r, u) for r in done for u in r.get("rejected", []) if u["id"] in labels]
    invalid = [(r, u) for r, u in applied if not labels[u["id"]].valid]
    kinds = Counter(labels[u["id"]].label for _, u in invalid)
    turns_with_invalid = {r["key"] for r, _ in invalid}
    edit_turns = [r for r in done if r.get("intent") == Intent.EDIT]
    wrongly_rejected = [(r, u) for r, u in rejected if labels[u["id"]].valid]
    return {
        "labelled_applied": len(applied),
        "invalid_applied": len(invalid),
        "invalid_rate": len(invalid) / len(applied) if applied else None,
        "invalid_by_kind": {kind: kinds[kind] for kind in KINDS if kinds.get(kind)},
        "edit_turns_with_invalid": len(turns_with_invalid),
        "edit_turn_invalid_rate": len(turns_with_invalid) / len(edit_turns) if edit_turns else None,
        "labelled_rejected": len(rejected),
        "wrongly_rejected": len(wrongly_rejected),
        "wrong_rejection_rate": len(wrongly_rejected) / len(rejected) if rejected else None,
    }


def summarize(records: Records, labels: Labels | None = None) -> dict[str, dict[str, Any]]:
    return {arm: summarize_arm(rows, labels) for arm, rows in _by_arm(records).items()}


# --- paired comparison -------------------------------------------------------

Statistic = Callable[[Records, Labels], float | None]


def invalid_unit_rate(turns: Records, labels: Labels) -> float | None:
    units = [u for r in turns if not r.get("error") for u in r.get("units", []) if u["id"] in labels]
    if not units:
        return None
    return len([u for u in units if not labels[u["id"]].valid]) / len(units)


def faulty_turn_rate(turns: Records, labels: Labels) -> float | None:
    done = [r for r in turns if not r.get("error")]
    if not done:
        return None
    return len([r for r in done if r.get("faults")]) / len(done)


def cost_per_turn(turns: Records, labels: Labels) -> float | None:
    done = [r for r in turns if not r.get("error")]
    return _mean([float(Decimal(r.get("cost_usd") or "0")) for r in done])


STATISTICS: dict[str, Statistic] = {
    "invalid_unit_rate": invalid_unit_rate,
    "faulty_turn_rate": faulty_turn_rate,
    "cost_per_turn": cost_per_turn,
}


def paired_bootstrap(
    records: Records,
    labels: Labels,
    arm_a: str,
    arm_b: str,
    statistic: Statistic,
    resamples: int = 2000,
    seed: int = 7,
) -> dict[str, Any] | None:
    """The difference `arm_a - arm_b` of a statistic, with a 95 % interval from
    resampling conversations. Paired: the same (pair, script) conversations are
    drawn for both arms, so what varies between the arms is the design, not the
    draw. The statistic is pooled over the drawn conversations, not averaged
    per conversation, so a conversation with ten units weighs ten."""
    by_arm = _by_arm(records)
    if arm_a not in by_arm or arm_b not in by_arm:
        return None
    a, b = _conversations(by_arm[arm_a]), _conversations(by_arm[arm_b])
    shared = sorted(set(a) & set(b))
    if not shared:
        return None

    def difference(keys: list[tuple[str, str]]) -> float | None:
        value_a = statistic([r for k in keys for r in a[k]], labels)
        value_b = statistic([r for k in keys for r in b[k]], labels)
        if value_a is None or value_b is None:
            return None
        return value_a - value_b

    point = difference(shared)
    if point is None:
        return None
    draws = random.Random(seed)
    samples: list[float] = []
    for _ in range(resamples):
        drawn = [shared[draws.randrange(len(shared))] for _ in shared]
        value = difference(drawn)
        if value is not None:
            samples.append(value)
    samples.sort()
    return {
        "arms": [arm_a, arm_b],
        "conversations": len(shared),
        "difference": point,
        "ci95": [samples[int(0.025 * len(samples))], samples[int(0.975 * len(samples)) - 1]] if samples else None,
        "resamples": len(samples),
    }


# --- agreement -----------------------------------------------------------------

def cohen_kappa(first: list[str], second: list[str]) -> float | None:
    if len(first) != len(second) or not first:
        return None
    total = len(first)
    observed = sum(1 for x, y in zip(first, second) if x == y) / total
    categories = set(first) | set(second)
    expected = sum(
        (first.count(c) / total) * (second.count(c) / total) for c in categories
    )
    if expected == 1:
        return 1.0
    return (observed - expected) / (1 - expected)


def system_agreement(records: Records, labels: Labels, arm: str) -> dict[str, Any] | None:
    """The judge (or the guard) against the human, on every labelled part the
    system saw: applied parts the system called ok, rejected parts it did not.
    Rejection is the positive class: precision is how often a rejection was
    deserved, recall how many of the invalid parts the system caught."""
    rows = [r for r in records if r["arm"] == arm and not r.get("error")]
    system: list[str] = []
    human: list[str] = []
    for record in rows:
        for unit in [*record.get("units", []), *record.get("rejected", [])]:
            label = labels.get(unit["id"])
            if label is None:
                continue
            system.append("ok" if unit["applied"] else "rejected")
            human.append("ok" if label.valid else "rejected")
    if not system:
        return None
    tp = sum(1 for s, h in zip(system, human) if s == "rejected" and h == "rejected")
    fp = sum(1 for s, h in zip(system, human) if s == "rejected" and h == "ok")
    fn = sum(1 for s, h in zip(system, human) if s == "ok" and h == "rejected")
    tn = len(system) - tp - fp - fn
    return {
        "arm": arm,
        "parts": len(system),
        "true_rejections": tp,
        "false_rejections": fp,
        "missed_invalid": fn,
        "agreed_ok": tn,
        "precision": tp / (tp + fp) if tp + fp else None,
        "recall": tp / (tp + fn) if tp + fn else None,
        "kappa": cohen_kappa(system, human),
    }


def inter_annotator(first: Labels, second: Labels) -> dict[str, Any] | None:
    shared = sorted(set(first) & set(second))
    if not shared:
        return None
    binary_a = [VALID if first[u].valid else "invalid" for u in shared]
    binary_b = [VALID if second[u].valid else "invalid" for u in shared]
    kinds_a = [first[u].label for u in shared]
    kinds_b = [second[u].label for u in shared]
    return {
        "units": len(shared),
        "agreement_valid_invalid": sum(1 for a, b in zip(binary_a, binary_b) if a == b) / len(shared),
        "kappa_valid_invalid": cohen_kappa(binary_a, binary_b),
        "agreement_kind": sum(1 for a, b in zip(kinds_a, kinds_b) if a == b) / len(shared),
        "kappa_kind": cohen_kappa(kinds_a, kinds_b),
    }


# --- report --------------------------------------------------------------------

def _fmt(value: Any) -> str:
    if value is None:
        return "–"
    if isinstance(value, float):
        return f"{value:.3f}"
    if isinstance(value, dict):
        return ", ".join(f"{k} {v}" for k, v in value.items()) or "–"
    return str(value)


ROWS = (
    ("conversations", "Conversations"),
    ("turns", "Turns"),
    ("failed_turns", "Failed turns"),
    ("edit_turns", "Edit turns"),
    ("turns_with_changes", "Turns that changed the resume"),
    ("applied_units", "Applied parts"),
    ("rejected_units", "Rejected parts"),
    ("rejected_by_kind", "Rejected, by system kind"),
    ("faulty_turns", "Turns with a structural fault"),
    ("faults_by_kind", "Structural faults, by kind"),
    ("gap_coverage_mean", "Gap coverage (mean per conversation)"),
    ("labelled_applied", "Applied parts labelled"),
    ("invalid_applied", "Applied parts labelled invalid"),
    ("invalid_rate", "Invalid rate over applied parts"),
    ("invalid_by_kind", "Invalid, by kind"),
    ("edit_turn_invalid_rate", "Edit turns with an invalid part"),
    ("labelled_rejected", "Rejected parts labelled"),
    ("wrongly_rejected", "Rejected parts labelled valid"),
    ("wrong_rejection_rate", "Wrong rejection rate"),
    ("model_calls_per_turn", "Model calls per turn"),
    ("tool_calls_per_turn", "Tool calls per turn"),
    ("tool_rejections", "Tool rejections (react)"),
    ("passes_per_edit_turn", "Passes per edit turn (guard, judge)"),
    ("cost_usd_per_turn", "Cost per turn (USD)"),
    ("latency_ms_per_turn", "Model latency per turn (ms)"),
    ("wall_ms_per_turn", "Wall time per turn (ms)"),
)


def render_markdown(
    summary: dict[str, dict[str, Any]],
    comparisons: list[dict[str, Any]] | None = None,
    agreements: list[dict[str, Any]] | None = None,
    annotators: dict[str, Any] | None = None,
) -> str:
    arms = list(summary)
    lines = ["| Metric | " + " | ".join(arms) + " |", "|---|" + "---|" * len(arms)]
    for key, title in ROWS:
        if not any(key in summary[arm] for arm in arms):
            continue
        lines.append(f"| {title} | " + " | ".join(_fmt(summary[arm].get(key)) for arm in arms) + " |")
    if comparisons:
        lines += ["", "| Difference | Conversations | Point | 95 % CI |", "|---|---|---|---|"]
        for c in comparisons:
            ci = c.get("ci95")
            lines.append(
                f"| {c['statistic']}: {c['arms'][0]} − {c['arms'][1]} | {c['conversations']} | "
                f"{_fmt(c['difference'])} | {_fmt(ci[0]) + ' to ' + _fmt(ci[1]) if ci else '–'} |"
            )
    if agreements:
        lines += ["", "| System vs human | Parts | True rej. | False rej. | Missed | Precision | Recall | Kappa |", "|---|---|---|---|---|---|---|---|"]
        for a in agreements:
            lines.append(
                f"| {a['arm']} | {a['parts']} | {a['true_rejections']} | {a['false_rejections']} | "
                f"{a['missed_invalid']} | {_fmt(a['precision'])} | {_fmt(a['recall'])} | {_fmt(a['kappa'])} |"
            )
    if annotators:
        lines += ["", "| Annotators | Units | Agreement valid/invalid | Kappa | Agreement on kind | Kappa on kind |", "|---|---|---|---|---|---|"]
        lines.append(
            f"| two sheets | {annotators['units']} | {_fmt(annotators['agreement_valid_invalid'])} | "
            f"{_fmt(annotators['kappa_valid_invalid'])} | {_fmt(annotators['agreement_kind'])} | {_fmt(annotators['kappa_kind'])} |"
        )
    return "\n".join(lines) + "\n"


def as_json(payload: Any) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2, default=str)
