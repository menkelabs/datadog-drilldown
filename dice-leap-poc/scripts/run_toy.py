#!/usr/bin/env python3
"""Run the dwave.md toy instance and append one SolveRecord line to runs/."""

from __future__ import annotations

import sys
from pathlib import Path

# package root: dice-leap-poc/
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from dice_leap_poc.pipeline import run_json_file, write_jsonl


def main() -> None:
    toy = ROOT / "sample_data" / "toy_dw_md.json"
    out = ROOT / "runs" / "mvp.jsonl"
    # Explicit QUBO path so the demo JSONL row shows SA vs baseline (toy JSON tier=simple would otherwise use heuristic).
    rec = run_json_file(toy, strategy_choice="qubo", num_reads=4000, seed=42)
    write_jsonl(rec, out)
    print(rec.to_json_line())


if __name__ == "__main__":
    main()
