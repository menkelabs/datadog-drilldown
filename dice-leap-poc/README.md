# dice-leap-poc (Milestone 1)

Python prototype: structured decision instances → **QUBO** → **local classical** solve (dwave-neal simulated annealing), with an optional **Leap hybrid** path (`dwave-system`).

**Goal:** [Sample fixtures](sample_data/README.md) simulate **when rollover to a QUBO/D-Wave-class solve makes sense** vs staying on the greedy heuristic (tier labels + `n`/edge thresholds from [dwave.md](../dwave.md)).

**Spec / roadmap:** [dwave.md](../dwave.md) · [milestones/milestone-1.md](../milestones/milestone-1.md)

## Install

```bash
cd dice-leap-poc
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

**Editable + dev tests:**

```bash
pip install -e ".[dev]"
```

**Optional Leap (Phase 2):**

```bash
pip install -e ".[dev,leap]"
export DWAVE_API_TOKEN="..."   # or `dwave setup`
```

## Run toy instance (JSONL)

```bash
python scripts/run_toy.py
```

Appends one **SolveRecord** line to `runs/mvp.jsonl` (directory gitignored) and prints JSON to stdout. The script **forces `strategy_choice=qubo`** so you still see simulated annealing vs baseline; the toy fixture has `tier: simple`, so **auto strategy** (default in `run_instance`) would use heuristic-only—use `scripts/batch_sample.py` for auto behavior across all fixtures.

## Batch + auto strategy

```bash
python scripts/batch_sample.py
```

Runs every `sample_data/*.json` with **automatic** strategy (`tier` + rollover metrics), appends to `runs/batch.jsonl`, and prints per-tier aggregates. Uses **`solver_mode=local_classical`** (default).

## L1 mirror (optional)

Copy JSONL from `runs/` next to Kotlin test-report layout (`embabel-dice-rca/test-reports/` is gitignored — local/CI artifact only):

```bash
# from repo root, after generating runs/*.jsonl
python dice-leap-poc/scripts/mirror_jsonl_l1.py
```

Writes to `embabel-dice-rca/test-reports/solver-runs/*.jsonl`. Override paths with `--source` / `--dest` if needed.

## Solver backends

| `solver_mode` | Backend | Default CI |
|---------------|---------|------------|
| `local_classical` | `neal.SimulatedAnnealingSampler` | Yes |
| `leap_hybrid` | `dwave.system.LeapHybridSampler` | No (needs `[leap]` + `DWAVE_API_TOKEN`) |

Python API:

```python
from dice_leap_poc.pipeline import run_instance
from dice_leap_poc.instance import Instance

inst = Instance.load_json("sample_data/toy_dw_md.json")
rec = run_instance(
    inst,
    strategy_choice="qubo",
    solver_mode="local_classical",
    num_reads=4000,
    seed=42,
)
# Leap: run_instance(..., solver_mode="leap_hybrid", leap_time_limit_s=5.0)
```

Heuristic-only runs always record `solver_mode=local_classical` (no sampler job).

## Strategy layer

- JSON **`tier`**: `simple` → `heuristic_only`; `complex` → `qubo`.
- If **`tier` is omitted**: rollover when `n_entities > 12` or `n_conflicts + n_dependencies > 8` (see [`dice_leap_poc/strategy.py`](dice_leap_poc/strategy.py), `RolloverConfig`).
- Optional instance field **`encoding_version`**: string copied into `SolveRecord.encoding_version` (default **`1`** from [`dice_leap_poc/record.py`](dice_leap_poc/record.py) when omitted). Bump when the QUBO mapping or record shape changes.

## Tests

Imports resolve via [pytest.ini](pytest.ini) (`pythonpath = .`). From `dice-leap-poc/`:

```bash
pytest tests/ -q
```

- **Leap cloud smoke** (optional): `pytest tests/test_leap.py -m leap -q` — requires `[leap]` and `DWAVE_API_TOKEN`.
- Default test run **skips** cloud Leap; includes a test that `leap_hybrid` without `dwave-system` raises `ImportError`.

## CI

Repository workflow [`.github/workflows/dice-leap-poc.yml`](../.github/workflows/dice-leap-poc.yml) installs `requirements.txt` only and runs `pytest` on changes under `dice-leap-poc/`.

## SolveRecord

Each run produces a JSON object (one line in JSONL) with:

| Field | Meaning |
|--------|---------|
| `instance_id` | Input fixture id |
| `solver_mode` | `local_classical` or `leap_hybrid` |
| `strategy_choice` | `qubo` or `heuristic_only` |
| `strategy_reason` | e.g. `fixture_tier_simple`, `rollover_metrics(...)`, `explicit_qubo` |
| `n_vars` | Binary variables |
| `objective` | Best QUBO energy found |
| `selected_decisions` | Entity ids with value 1 |
| `runtime_ms` | Solve wall time (0 for heuristic-only) |
| `baseline_objective` | Greedy heuristic energy |
| `vs_baseline_delta` | `baseline_objective - objective` (positive ⇒ optimizer better) |
| `encoding_version` | QUBO/record contract revision (default `1`; override via instance JSON) |
| `tier` | Optional fixture label (`simple` / `complex`) |

JSON Schema: [schemas/solve_record.schema.json](schemas/solve_record.schema.json)

## Instance JSON format

See [sample_data/toy_dw_md.json](sample_data/toy_dw_md.json):

- `entities`: `{ id, cost, signal }`
- `conflicts`: `[ [a, b], ... ]` — penalty if both selected
- `dependencies`: `[ [dependent, required], ... ]` — soft constraint via QUBO penalty

## Follow-on

- Optional **persist L2** (H2 / test-report-server) if JSONL is not enough.
- [docs/DWAVE_REAL_WORLD_METRICS.md](../docs/DWAVE_REAL_WORLD_METRICS.md) for pilot/production logging.
