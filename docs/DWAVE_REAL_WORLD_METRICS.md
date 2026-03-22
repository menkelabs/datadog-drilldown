# Real-world metrics for DICE + QUBO / Leap pilots

**Status:** Planning doc (Milestone 1). Implement collection **after** PoC acceptance on synthetic tiered data.

**Related:** [dwave.md](../dwave.md), [milestones/milestone-1.md](../milestones/milestone-1.md), [dice-leap-poc/README.md](../dice-leap-poc/README.md).  
**Implemented v1 (JVM):** [QUBO_METRICS_V1.md](QUBO_METRICS_V1.md) (Milestone 2b).

## 1. Problem / context

| Field | Use |
|--------|-----|
| `case_id` | Correlate with incident / RCA case |
| Time window | Ingestion / investigation span |
| Services / entities | Domain scope |
| Data sources | Logs, traces, DICE propositions, etc. |
| Scenario taxonomy | Optional labels (latency, deploy, dependency, …) |

## 2. Instance complexity

| Field | Use |
|--------|-----|
| `n_candidates` | Binary decision variables |
| `n_constraint_edges` | Conflicts + dependencies (or graph metric you standardize) |
| Graph density | Optional normalized metric |
| `strategy_choice` / `strategy_reason` | What the strategy layer chose |
| Encoding version | QUBO builder / schema version id |

## 3. Baseline path

| Field | Use |
|--------|-----|
| Heuristic output | Selected ids, ordering, scores |
| Latency | End-to-end baseline ms |
| Domain summary | Human- or rule-aligned explanation for fair compare to QUBO interpretation |

## 4. QUBO / solver path

| Field | Use |
|--------|-----|
| `n_vars` | As in `SolveRecord` |
| Compile + solve latency | Wall time; split if useful |
| `solver_mode` | `local_classical` / `leap_hybrid` |
| Sampler params | `num_reads`, temperature schedule, hybrid options |
| Objective / energy | Best sample energy |
| Sample metadata | Optional top-k energies |
| Leap job id / cost | If Phase 2 |
| Post-interpret violations | Domain constraints not fully captured in QUBO |

## 5. Outcomes / labels

| Field | Use |
|--------|-----|
| SRE acceptance | Did the recommendation get used / overridden |
| MTTR proxies | Time-to-mitigate where attributable |
| Ratings | Optional human quality score |
| Ground truth | When known (e.g., actual root cause set) |
| Stability | Same instance, fixed seed, variance across reruns |

## 6. Operations

| Field | Use |
|--------|-----|
| Errors / timeouts | Solver or API failures |
| Fallback | Switched to heuristic path |
| Rate limits | Leap / API throttling |

## 7. Governance

- PII / secrets: redact in stored JSONL; policy for retention.
- Access: who can read solver runs and link to cases.

## Enables

- Leap cost/benefit vs local classical.
- Complexity → rollover prediction (calibrate thresholds on real distributions).
- Real-case uplift vs baseline (`vs_baseline_delta` in production).
- Regression detection on model/threshold/encoding changes.
