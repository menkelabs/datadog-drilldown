#!/usr/bin/env python3
"""Run all sample_data/*.json with auto strategy; append JSONL and print tier summary."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dice_leap_poc.batch import iter_fixture_paths, run_fixtures, tier_aggregates


def main() -> None:
    sample = ROOT / "sample_data"
    out = ROOT / "runs" / "batch.jsonl"
    paths = iter_fixture_paths(sample)
    if not paths:
        print("No fixtures in", sample, file=sys.stderr)
        sys.exit(1)
    records = run_fixtures(paths, append_jsonl=out, num_reads=10000, seed=42)
    agg = tier_aggregates(records)
    print("Wrote", len(records), "lines to", out)
    for tier, stats in agg.items():
        print(tier, stats)


if __name__ == "__main__":
    main()
