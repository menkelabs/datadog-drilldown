"""Batch all JSON fixtures → JSONL + optional tier aggregates."""

from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from dice_leap_poc.instance import Instance
from dice_leap_poc.pipeline import run_instance, write_jsonl
from dice_leap_poc.record import SolveRecord


def iter_fixture_paths(sample_dir: Path, glob: str = "*.json") -> list[Path]:
    return sorted(p for p in sample_dir.glob(glob) if p.is_file())


def run_fixtures(
    paths: list[Path],
    *,
    append_jsonl: Path | None = None,
    **run_kwargs: Any,
) -> list[SolveRecord]:
    records: list[SolveRecord] = []
    for p in paths:
        inst = Instance.load_json(p)
        rec = run_instance(inst, **run_kwargs)
        records.append(rec)
        if append_jsonl is not None:
            write_jsonl(rec, append_jsonl)
    return records


def tier_aggregates(records: list[SolveRecord]) -> dict[str, dict[str, float | int]]:
    """Mean vs_baseline_delta and count per tier label (None → 'unknown')."""
    by_tier: dict[str, list[SolveRecord]] = defaultdict(list)
    for r in records:
        t = r.tier if r.tier is not None else "unknown"
        by_tier[t].append(r)

    out: dict[str, dict[str, float | int]] = {}
    for tier, rs in sorted(by_tier.items()):
        deltas = [r.vs_baseline_delta for r in rs]
        out[tier] = {
            "count": len(rs),
            "mean_vs_baseline_delta": sum(deltas) / len(deltas) if deltas else 0.0,
            "min_vs_baseline_delta": min(deltas) if deltas else 0.0,
            "max_vs_baseline_delta": max(deltas) if deltas else 0.0,
        }
    return out


def summarize_to_json(records: list[SolveRecord]) -> str:
    payload = {
        "runs": [r.to_json_dict() for r in records],
        "tiers": tier_aggregates(records),
    }
    return json.dumps(payload, indent=2, sort_keys=True)
