# Sample data — simulating QUBO / D-Wave rollover

These JSON instances exist to **simulate when switching from a cheap heuristic to a QUBO solve (local SA or D-Wave Leap) is justified**, aligned with [dwave.md](../../dwave.md) and [milestones/milestone-1.md](../../milestones/milestone-1.md).

| File | Role |
|------|------|
| **`toy_dw_md.json`** | **Tier `simple`** → strategy **heuristic_only** (no QUBO cost). Small domain from the spec. |
| **`complex_dw_md.json`** | **Tier `complex`** → strategy **qubo**; greedy baseline is **worse** than optimized energy (simulates “optimization pays off”). |
| **`below_rollover_n12_e8.json`** | **No `tier`** — at the **upper bound** of heuristic-only: `n=12` candidates, `8` constraint edges (not above thresholds). |
| **`rollover_n13.json`** | **No `tier`** — **`n=13 > 12`** triggers **metrics rollover** → **qubo**; conflicts tuned so **SA beats greedy** by ~2+ energy (not just strategy flip). |
| **`rollover_edges9.json`** | **No `tier`** — **`n=10`**, **`9` edges > 8** → **qubo** (dense-interaction / constraint-edge story). |

Rollover rules in code: [`dice_leap_poc/strategy.py`](../dice_leap_poc/strategy.py) (`RolloverConfig`).

Use **`scripts/batch_sample.py`** to run all fixtures and inspect JSONL `strategy_reason` / `vs_baseline_delta`.
