# QUBO solver as an agent workflow node (M2c)

**Status:** Contract + resilience layer; full Embabel DAG wiring is a follow-up.  
**Related:** [docs/architecture/agent-workflow.puml](architecture/agent-workflow.puml) (Embabel RCA flow), [ADR 0002](adr/0002-dice-leap-subprocess-bridge.md).

## D1 — Workflow model (inputs / outputs)

Treat **QUBO enrichment** as a logical node:

| Direction | Artifact |
|-----------|----------|
| **In** | `Report` after `buildReport`: ranked `candidates`, `IncidentContext.id`, optional tier (future). |
| **Transform** | `RcaCandidateToQuboInstanceMapper` → `QuboInstancePayload` JSON on disk. |
| **Out** | Same `Report` with `findings["qubo"]` populated: `strategy_*`, optional `solve_record`, `selected_candidate_titles`, `fallback` flags. |
| **Side effect** | Python subprocess (`solve_json.py`) → stdout `SolveRecord` JSON line. |

This matches the DAG mental model **instance in → SolveRecord out**, even though today it is invoked synchronously from `RcaAgent` after the main RCA steps.

## D2 — Idempotency, timeouts, fallback

| Mechanism | Implementation |
|-----------|----------------|
| **Timeout** | `embabel.rca.qubo.subprocess-timeout-seconds` (process destroyed if exceeded). |
| **Retries** | `embabel.rca.qubo.max-subprocess-attempts` (default 2). |
| **Fallback** | If all attempts fail and `fail-on-solver-error=false`, set `findings.qubo.fallback=true` and `selected_candidate_titles` to top-N RCA candidates by score (`fallback-candidate-count`). |
| **Strict mode** | `fail-on-solver-error=true` throws after failures (no fallback). |

Idempotency: each analysis run builds a fresh temp instance file; no shared mutable solver state on the JVM.

## D3 — Node observability

- **Micrometer:** `qubo.subprocess` timer, `qubo.enrichment` counter (tag `outcome`).
- **Logs:** `qubo_telemetry` structured line (see [QUBO_METRICS_V1.md](QUBO_METRICS_V1.md)).

Optional later: OpenTelemetry span `qubo.solve` around subprocess (not in v1).
