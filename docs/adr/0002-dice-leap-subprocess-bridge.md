# ADR 0002: dice-leap-poc integration via subprocess (M2a)

## Status

Accepted (Milestone 2a)

## Context

We need the JVM RCA path (`embabel-dice-rca`) to run the QUBO pipeline defined in Python (`dice-leap-poc`: instance JSON → BQM → simulated annealing / optional Leap) without duplicating the compiler and samplers in Kotlin.

Alternatives considered:

1. **Subprocess** — JVM writes instance JSON, invokes `scripts/solve_json.py`, reads one JSON line (`SolveRecord`).
2. **HTTP sidecar** — Python microservice; extra deployable and ops surface.
3. **Port QUBO + solvers to Kotlin/JVM** — highest fidelity long-term, large effort.

## Decision

Use **subprocess** first:

- Script: [`dice-leap-poc/scripts/solve_json.py`](../../dice-leap-poc/scripts/solve_json.py)
- Working directory: `dice-leap-poc` root (config: `embabel.rca.qubo.dice-leap-poc-root`)
- `PYTHONPATH` set to that root so the package imports without a venv (CI/dev should `pip install -e .` or set path consistently)
- Interpreter: `embabel.rca.qubo.python-executable` (default `python3`)

## Consequences

- **Pros:** Reuses tested Python pipeline and `SolveRecord` contract; smallest integration slice.
- **Cons:** Requires Python + deps on hosts that enable QUBO; latency of process spawn; path configuration.
- **Feature flag:** `embabel.rca.qubo.enabled` defaults to `false` until rollout.

## Follow-ups

- M2c: optional HTTP sidecar or gRPC if process boundaries become painful.
- M2d: document CI matrix (Java-only vs Java+Python smoke).
