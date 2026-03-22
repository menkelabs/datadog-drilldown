# Milestone 2 — Live DICE / RCA integration, ops hardening, agent DAG

**Status:** M2a–M2d first pass complete (hardening / OTel / full Embabel DAG wiring may follow)  
**Builds on:** [Milestone 1](milestone-1.md) (PoC, contract, optional Leap, JSONL/H2 lite, Kotlin ingest types)  
**Spec:** [dwave.md](../dwave.md) · Metrics plan: [docs/DWAVE_REAL_WORLD_METRICS.md](../docs/DWAVE_REAL_WORLD_METRICS.md)

## Phased delivery (M2a → M2d)

Work is split so you can **ship a thin vertical slice first**, then **harden** with metrics and DAG shape, then **platform** CI/Maven.

| Phase | Name | Intent | Exit criteria (suggested) |
|-------|------|--------|---------------------------|
| **M2a** | **Integration spine** | RCA context → instance → solver → result back into the agent path (behind flags). | End-to-end demo on **recorded** fixtures: JVM emits instance JSON, invokes solver (subprocess/HTTP per ADR), parses `SolveRecord`, updates context; IT green without live Datadog. |
| **M2b** | **Observability** | Implement real-world metrics emission (not docs-only). | v1 field set logged or metered from solver path; sample queries or DD facets documented; links to JSONL/H2 where duplicated. |
| **M2c** | **DAG node & resilience** | Solver as **explicit** workflow node + timeouts / fallback / idempotency. | Orchestration diagram or code shows named node; policy for failure ≠ hard fail (e.g. heuristic fallback); spans/metrics at node boundary. |
| **M2d** | **Platform & CI** | Leap **opt-in** in CI; Maven umbrella + unified Java workflow. | Optional GH job when `DWAVE_API_TOKEN` set; root `pom.xml` (optional) + `java-modules.yml` (or equivalent) runs tests across Java modules with agreed skip-IT profile. |

**Suggested order:** **M2a** first (proves value), **M2b** in parallel once the first solver call exists, **M2c** refactors M2a hook into a proper node, **M2d** anytime but often last to avoid blocking feature work.

```mermaid
flowchart LR
  M2a[M2a Integration spine]
  M2b[M2b Observability]
  M2c[M2c DAG node]
  M2d[M2d Platform CI]
  M2a --> M2b
  M2a --> M2c
  M2b --> M2c
  M2c --> M2d
```

*(M2b can start after M2a has a single solver invocation; M2d can proceed in parallel if resourced.)*

---

## Purpose

Close the gap between **synthetic PoC** and **production-shaped** behavior:

1. **Wire real investigation context** from the Embabel/DICE RCA path into the **QUBO compiler path** (today: *no* automatic wiring from live Datadog-shaped runs into `dice-leap-poc` semantics inside `embabel-dice-rca` agents).
2. **Keep Leap cloud opt-in** (`DWAVE_API_TOKEN`); optionally add **CI jobs** that run only when secrets are present (never block default PRs).
3. **Implement** real-world metrics in **application/telemetry** (Milestone 1 only **documented** what to collect).
4. Treat the **solver as an explicit node** in the **agent / workflow DAG** (not built today).
5. **Repo engineering:** optional **Maven parent / aggregator** + **umbrella GitHub Actions** for all Java modules (today: **per-module** POMs; **path-scoped** workflows only).

---

## Work items by phase (checklist)

### M2a — Integration spine (`embabel-dice-rca` + solver boundary)

- [x] **A1 — Instance model in JVM** — `QuboInstancePayload` + `RcaCandidateToQuboInstanceMapper` map ranked candidates → dice-leap-poc JSON (`encoding_version` **1**).
- [x] **A2 — Extraction hook** — `RcaAgent` calls `QuboReportEnricher` after `buildReport` (rollover via `QuboRolloverPlanner`, aligned with Python thresholds).
- [x] **A3 — Solver invocation** — ADR [docs/adr/0002-dice-leap-subprocess-bridge.md](../docs/adr/0002-dice-leap-subprocess-bridge.md); `DiceLeapPythonSolver` runs `dice-leap-poc/scripts/solve_json.py` + `SolveRecordJsonlReader.parseLine`.
- [x] **A4 — Result interpreter** — `findings["qubo"]` includes `solve_record`, `selected_candidate_titles`, and an extra **recommendation** line when selections exist (deeper propositions/UI later).
- [x] **A5 — Feature flags** — `embabel.rca.qubo.enabled` default **false**; optional IT `DiceLeapPythonSolverIntegrationTest` if `DICE_LEAP_POC_ROOT` is set.

### M2b — Observability (metrics implementation)

- [x] **C1 — Field list** — [docs/QUBO_METRICS_V1.md](../docs/QUBO_METRICS_V1.md) freezes v1 fields (aligned with [DWAVE_REAL_WORLD_METRICS.md](../docs/DWAVE_REAL_WORLD_METRICS.md)).
- [x] **C2 — Emit** — `qubo_telemetry` structured log lines + Micrometer `qubo.subprocess` / `qubo.enrichment` from `QuboReportEnricher` / `DiceLeapPythonSolver`.
- [x] **C3 — Dashboards / queries** — Datadog / PromQL examples in [QUBO_METRICS_V1.md](../docs/QUBO_METRICS_V1.md).

### M2c — Agent DAG node & resilience

- [x] **D1 — Workflow model** — [docs/QUBO_DAG_NODE.md](../docs/QUBO_DAG_NODE.md) documents inputs/outputs (instance → `SolveRecord` → `findings.qubo`).
- [x] **D2 — Idempotency & timeouts** — Subprocess timeout, `max-subprocess-attempts`, retry delay; **fallback** top-N candidates when solver fails (`fallback-on-solver-failure`).
- [x] **D3 — Node observability** — Micrometer timers/counters at subprocess boundary (OTel span optional later).

### M2d — Leap CI + Maven / CI umbrella

- [x] **B1 — Document** — [dice-leap-poc/README.md](../dice-leap-poc/README.md) Leap section + workflow comments (token, optional job).
- [x] **B2 — Optional workflow** — [.github/workflows/dice-leap-poc-leap.yml](../.github/workflows/dice-leap-poc-leap.yml) (`workflow_dispatch` + PR path filter; skips when no `DWAVE_API_TOKEN`).
- [x] **B3 — Policy** — Leap workflow is **non-required** / opt-in; default PR checks remain [dice-leap-poc.yml](../.github/workflows/dice-leap-poc.yml) local-only.
- [x] **E1 — Parent POM** — Root [pom.xml](../pom.xml) aggregates `embabel-dice-rca`, `dice-server`, `test-report-server`.
- [x] **E2 — Unified workflow** — [.github/workflows/java-modules.yml](../.github/workflows/java-modules.yml) full reactor `mvn test`, Java 21, Maven cache.
- [x] **E3 — Path strategy** — Path filters on `pom.xml` + each module + workflow file (full reactor when any Java module changes).

---

## Goals (Milestone 2 overall)

1. **End-to-end RCA → QUBO → back** on **realistic** (then real) data, behind flags (**M2a**).
2. **Measurable** pilots via implemented telemetry (**M2b**).
3. **Composable** agents: solver as a **first-class** step (**M2c**).
4. **Operable Leap** + **maintainable** multi-module CI (**M2d**).

## Non-goals (unless explicitly added)

- Replacing the entire RCA stack with QUBO-only logic.
- On-QPU production workloads.
- Perfect optimality on messy real graphs.

## Dependencies

- Stable **`SolveRecord`** + `encoding_version` (Milestone 1).
- **A3** ADR locked early in **M2a**.

## Risks

- Leap **latency/quota**; **PII** in logged instances.
- **DAG** complexity (duplicate calls, stale context).
- **Maven reactor** + flaky ITs — use `-DskipITs` profile until stable.

## References

- [milestone-1.md](milestone-1.md)  
- [dwave.md](../dwave.md)  
- [docs/QUBO_METRICS_V1.md](../docs/QUBO_METRICS_V1.md)  
- [docs/QUBO_DAG_NODE.md](../docs/QUBO_DAG_NODE.md)  
- [dice-leap-poc/README.md](../dice-leap-poc/README.md)  
- [test-report-server/README.md](../test-report-server/README.md)  

---

## Plan evolution

- **2026-01-25:** Milestone 2 drafted (gap analysis).
- **2026-01-25:** Split into **M2a–M2d** phases with exit criteria and checklist mapping.
- **2026-01-25:** M2a first slice: subprocess bridge, mapper, enricher, `solve_json.py`, ADR 0002.
- **2026-01-25:** M2b–M2d: `QUBO_METRICS_V1` + Micrometer, `QUBO_DAG_NODE`, retries/fallback, root `pom.xml`, `java-modules.yml`, optional `dice-leap-poc-leap.yml`.
