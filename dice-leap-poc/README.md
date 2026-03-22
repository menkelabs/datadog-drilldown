# dice-leap-poc (Milestone 1, Phase 1)

Python prototype: structured decision instances → **QUBO** → **local classical** solve (dwave-neal simulated annealing). **No Leap** in Phase 1.

**Spec / roadmap:** [dwave.md](../dwave.md) · [milestones/milestone-1.md](../milestones/milestone-1.md)

## Install

```bash
cd dice-leap-poc
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
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

Runs every `sample_data/*.json` with **automatic** strategy (`tier` + rollover metrics), appends to `runs/batch.jsonl`, and prints per-tier aggregates.

## Strategy layer

- JSON **`tier`**: `simple` → `heuristic_only`; `complex` → `qubo`.
- If **`tier` is omitted**: rollover when `n_entities > 12` or `n_conflicts + n_dependencies > 8` (see [`dice_leap_poc/strategy.py`](dice_leap_poc/strategy.py), `RolloverConfig`).

Editable install (optional):

```bash
pip install -e ".[dev]"
```

## Tests

Imports resolve via [pytest.ini](pytest.ini) (`pythonpath = .`). From `dice-leap-poc/`:

```bash
pytest tests/ -q
```

## SolveRecord (Phase 1)

Each run produces a JSON object (one line in JSONL) with:

| Field | Meaning |
|--------|---------|
| `instance_id` | Input fixture id |
| `solver_mode` | `local_classical` (Phase 1 only) |
| `strategy_choice` | `qubo` or `heuristic_only` |
| `strategy_reason` | Why that strategy was chosen (MVP uses `phase1_mvp_always_qubo`) |
| `n_vars` | Binary variables |
| `objective` | Best QUBO energy found |
| `selected_decisions` | Entity ids with value 1 |
| `runtime_ms` | Local solve wall time |
| `baseline_objective` | Greedy heuristic energy |
| `vs_baseline_delta` | `baseline_objective - objective` (positive ⇒ QUBO better) |
| `tier` | Optional fixture label (`simple` / `complex` later) |

JSON Schema: [schemas/solve_record.schema.json](schemas/solve_record.schema.json)

## Instance JSON format

See [sample_data/toy_dw_md.json](sample_data/toy_dw_md.json):

- `entities`: `{ id, cost, signal }`
- `conflicts`: `[ [a, b], ... ]` — penalty if both selected
- `dependencies`: `[ [dependent, required], ... ]` — soft constraint via QUBO penalty

## Next (per milestone)

- Tiered **complex** fixtures + strategy rollover rules + acceptance tests (`vs_baseline_delta` margins).
- Optional **Leap** (`leap_hybrid`) after local path is stable.
- `docs/DWAVE_REAL_WORLD_METRICS.md` for pilot/production logging.
