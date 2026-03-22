# QUBO solver as an agent workflow node (M2c + follow-ups)

**Status:** Implemented for **RcaAgent** (Datadog pipeline) and **IncidentInvestigatorAgent** (Embabel shell).  
**Related:** [docs/architecture/agent-workflow.puml](architecture/agent-workflow.puml), [ADR 0002](adr/0002-dice-leap-subprocess-bridge.md), [CI_BRANCH_PROTECTION.md](CI_BRANCH_PROTECTION.md).

## D1 — Workflow model (inputs / outputs)

### Path A — `RcaAgent` (monitor / logs / service analysis)

| Direction | Artifact |
|-----------|----------|
| **In** | `Report` after `buildReport`: ranked `candidates`, `IncidentContext.id`, optional tier (future). |
| **Transform** | `RcaCandidateToQuboInstanceMapper` → `QuboInstancePayload` JSON on disk. |
| **Out** | Same `Report` with `findings["qubo"]` populated: `strategy_*`, optional `solve_record`, `selected_candidate_titles`, `fallback` flags. |
| **Side effect** | Python subprocess (`solve_json.py`) → stdout `SolveRecord` JSON line. |

### Path B — `IncidentInvestigatorAgent` (Embabel DAG)

| Step | Embabel `@Action` | Notes |
|------|-------------------|--------|
| After critique accepts | `runQuboShortlist` | `pre = analysisSatisfactory`, `post = quboNodeDone`. |
| Final report | `generateReport` | `pre = quboNodeDone`; prompt includes `QuboShortlistResult.promptSection()`. |

[`EmbabelQuboNodeService`](../embabel-dice-rca/src/main/kotlin/com/example/rca/agent/EmbabelQuboNodeService.kt) maps `RootCauseAnalysis.candidates` → domain [`Candidate`](../embabel-dice-rca/src/main/kotlin/com/example/rca/domain/Candidate.kt) and reuses [`QuboReportEnricher`](../embabel-dice-rca/src/main/kotlin/com/example/rca/dice/qubo/QuboReportEnricher.kt). When `embabel.rca.qubo.enabled=false`, the node returns `QuboShortlistResult(skipped=true)` immediately so the DAG always reaches `generateReport`.

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
- **Tracing:** Micrometer **Observation** `qubo.python.solve` (contextual name `qubo-python-solve`, low-cardinality tag `case.id`) wraps the Python subprocess in [`QuboObservationHelper`](../embabel-dice-rca/src/main/kotlin/com/example/rca/dice/qubo/QuboObservationHelper.kt). With `micrometer-tracing-bridge-otel` on the classpath, this maps to an **OpenTelemetry span** when a tracer is configured (e.g. OTLP exporter via Spring Boot `management.otlp.tracing.*` or an OTel Java agent).

Sampling: `management.tracing.sampling.probability` (default `0.1` unless overridden by `TRACE_SAMPLING_PROBABILITY`).
