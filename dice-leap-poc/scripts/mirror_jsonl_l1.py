#!/usr/bin/env python3
"""
Copy JSONL solver runs from dice-leap-poc/runs/ to embabel-dice-rca L1 mirror.

Target layout (gitignored under embabel-dice-rca): ``test-reports/solver-runs/``.
Run from repo root or from dice-leap-poc; safe to run after ``batch_sample.py`` / ``run_toy.py``.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


def repo_root_from_script() -> Path:
    # dice-leap-poc/scripts/mirror_jsonl_l1.py -> repo root is parents[2]
    return Path(__file__).resolve().parents[2]


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--dest",
        type=Path,
        default=None,
        help="Override destination directory (default: <repo>/embabel-dice-rca/test-reports/solver-runs)",
    )
    p.add_argument(
        "--source",
        type=Path,
        default=None,
        help="Override source directory (default: <repo>/dice-leap-poc/runs)",
    )
    args = p.parse_args()
    root = repo_root_from_script()
    src = args.source or (root / "dice-leap-poc" / "runs")
    dest = args.dest or (root / "embabel-dice-rca" / "test-reports" / "solver-runs")

    if not src.is_dir():
        print("Source not found:", src, file=sys.stderr)
        sys.exit(1)

    dest.mkdir(parents=True, exist_ok=True)
    copied = 0
    for f in sorted(src.glob("*.jsonl")):
        shutil.copy2(f, dest / f.name)
        copied += 1
        print(f.name, "->", dest / f.name)

    if not copied:
        print("No *.jsonl files in", src, file=sys.stderr)
        sys.exit(1)

    print("Copied", copied, "file(s) to", dest)


if __name__ == "__main__":
    main()
