#!/usr/bin/env python3
"""
Solve one dice-leap-poc Instance JSON file; print a single SolveRecord JSON line to stdout.

Used by embabel-dice-rca (M2a) via subprocess. Run from repo with PYTHONPATH including
this script's parent directory (the dice-leap-poc folder), or install the package editable.

Example:
  PYTHONPATH=. python3 scripts/solve_json.py --input sample_data/toy_dw_md.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# Package root: dice-leap-poc/
_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from dice_leap_poc.instance import Instance  # noqa: E402
from dice_leap_poc.pipeline import run_instance  # noqa: E402


def main() -> None:
    p = argparse.ArgumentParser(description="Solve one instance JSON; print SolveRecord JSON line.")
    p.add_argument("--input", required=True, help="Path to instance .json")
    p.add_argument(
        "--strategy-choice",
        default=None,
        choices=("qubo", "heuristic_only"),
        help="Override auto strategy (default: plan from tier + rollover metrics)",
    )
    p.add_argument(
        "--solver-mode",
        default="local_classical",
        choices=("local_classical", "leap_hybrid"),
    )
    p.add_argument("--num-reads", type=int, default=2000)
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    inst = Instance.load_json(args.input)
    rec = run_instance(
        inst,
        strategy_choice=args.strategy_choice,
        solver_mode=args.solver_mode,
        num_reads=args.num_reads,
        seed=args.seed,
    )
    print(rec.to_json_line(), flush=True)


if __name__ == "__main__":
    main()
