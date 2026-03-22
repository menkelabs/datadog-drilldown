# QUBO / solver telemetry — v1 field set (M2b)

**Status:** Implemented in `embabel-dice-rca` when `embabel.rca.qubo.enabled=true`.  
**Related:** [DWAVE_REAL_WORLD_METRICS.md](DWAVE_REAL_WORLD_METRICS.md), [milestones/milestone-2.md](../milestones/milestone-2.md).

## 1. Frozen minimum (v1)

These fields are emitted on **structured log** lines prefixed with `qubo_telemetry` and mirrored in **Micrometer** (`qubo.*` meters).

| Field | Description |
|--------|-------------|
| `case_id` | Incident / report id (RCA context id). |
| `encoding_version` | QUBO instance / record encoding (e.g. `1`). |
| `strategy_choice` | `qubo`, `heuristic_only`, or `skipped`. |
| `strategy_reason` | Rollover / tier reason string. |
| `solver_mode` | `local_classical`, `leap_hybrid`, or `none` if not run. |
| `n_candidates` | RCA candidates mapped to entities. |
| `n_constraint_edges` | Conflicts + dependencies in built instance. |
| `subprocess_ms` | Wall time for Python subprocess (0 if not run). |
| `runtime_ms` | Solver runtime from `SolveRecord` when present. |
| `objective` | Best QUBO energy when present. |
| `baseline_objective` | Heuristic baseline when present. |
| `vs_baseline_delta` | `baseline - objective` when present. |
| `outcome` | `success`, `failure`, `skipped_no_candidates`, `skipped_rollover`, `fallback_heuristic`. |
| `error` | Short error message when `outcome=failure` or after retries. |
| `attempt` | Subprocess attempt index (1-based) on success. |

## 2. Micrometer names

| Meter | Type | Tags |
|--------|------|------|
| `qubo.subprocess` | Timer | `outcome` |
| `qubo.enrichment` | Counter | `outcome` |

## 3. Datadog log search examples (C3)

Assume JSON or key=value parsing from raw message, or map log pipeline to extract fields.

**All QUBO telemetry:**

```text
service:embabel-dice-rca "qubo_telemetry"
```

**Failures only:**

```text
"qubo_telemetry" outcome:failure
```

**Cases where solver beat baseline (`vs_baseline_delta` > 0):**

```text
"qubo_telemetry" outcome:success vs_baseline_delta:>0
```

**Adjust** `service:` to your service tag.

## 4. Grafana / PromQL (if scraping `/actuator/prometheus`)

```promql
sum(rate(qubo_enrichment_total[5m])) by (outcome)
```

```promql
histogram_quantile(0.95, sum(rate(qubo_subprocess_seconds_bucket[5m])) by (le, outcome))
```

*(Exact metric names may be dot-escaped as `qubo_subprocess_seconds` depending on registry naming.)*
