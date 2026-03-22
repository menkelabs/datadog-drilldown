# Milestone 2 — Live DICE / RCA integration, ops hardening, agent DAG

**Status:** Planned  
**Builds on:** [Milestone 1](milestone-1.md) (PoC, contract, optional Leap, JSONL/H2 lite, Kotlin ingest types)  
**Spec:** [dwave.md](../dwave.md) · Metrics plan: [docs/DWAVE_REAL_WORLD_METRICS.md](../docs/DWAVE_REAL_WORLD_METRICS.md)

## Purpose

Close the gap between **synthetic PoC** and **production-shaped** behavior:

1. **Wire real investigation context** from the Embabel/DICE RCA path into the **QUBO compiler path** (today: *no* automatic wiring from live Datadog-shaped runs into `dice-leap-poc` semantics inside `embabel-dice-rca` agents).
2. **Keep Leap cloud opt-in** (`DWAVE_API_TOKEN`); optionally add **CI jobs** that run only when secrets are present (never block default PRs).
3. **Implement** real-world metrics in **application/telemetry** (Milestone 1 only **documented** what to collect).
4. Treat the **solver as an explicit node** in the **agent / workflow DAG** (not built today).
5. **Repo engineering:** optional **Maven parent / aggregator** + **umbrella GitHub Actions** so **all** Java modules (`embabel-dice-rca`, `dice-server`, `test-report-server`, …) are built/tested on a defined schedule or path set (today: **per-module** POMs; workflows are **path-scoped**, not one unified Java pipeline).

---

## Work items (checklist)

### A — Live DICE / RCA → QUBO path (`embabel-dice-rca`)

- [ ] **A1 — Instance model in JVM** — Map RCA artifacts (candidates, conflicts, deps, costs/signals) to a JSON schema compatible with `dice-leap-poc` / `SolveRecord` contract (reuse or version `encoding_version`).
- [ ] **A2 — Extraction hook** — One bounded step in the investigation pipeline that **emits** an instance (file, message, or internal DTO) when rollover rules say QUBO is justified (mirror Python `strategy` semantics or call shared rules).
- [ ] **A3 — Solver invocation** — Process boundary: **subprocess** to Python `dice-leap-poc` *or* **HTTP sidecar** *or* **ported compiler** in Kotlin (decision record in ADR). Start with smallest integration (e.g. JSON file + `ProcessBuilder` + JSONL parse back).
- [ ] **A4 — Result interpreter** — Map `SolveRecord` / selected decisions back into agent context (propositions, next actions, or UI payload).
- [ ] **A5 — Feature flags** — Disable-by-default in prod until acceptance; integration tests with **recorded** Datadog-style fixtures (not live API in CI).

### B — Leap in CI / cloud (opt-in)

- [ ] **B1 — Document** — README: token setup, quota, when to enable Leap vs local SA.
- [ ] **B2 — Optional workflow** — e.g. `workflow_dispatch` or `pull_request` with **secret present** check; job installs `[leap]`, runs `pytest -m leap` or one smoke; **skip** if `DWAVE_API_TOKEN` missing.
- [ ] **B3 — Never default** — Main branch protection stays **local-only** for `dice-leap-poc` PR checks unless team explicitly promotes Leap job to required.

### C — Real-world metrics (implement, not only docs)

- [ ] **C1 — Field list** — Freeze minimum set from [DWAVE_REAL_WORLD_METRICS.md](../docs/DWAVE_REAL_WORLD_METRICS.md) for v1 (case id, encoding version, strategy, solver mode, latencies, deltas, errors).
- [ ] **C2 — Emit** — Log/OTel/Micrometer or structured logs from JVM path when solver runs; align with JSONL/H2 if duplicated.
- [ ] **C3 — Dashboards / queries** — Optional: Datadog log facets or Grafana; document query examples in repo.

### D — Solver as a node in the agent DAG

- [ ] **D1 — Workflow model** — Identify Embabel/DICE DAG or orchestration point; define **inputs/outputs** of the solver node (instance in → `SolveRecord` out).
- [ ] **D2 — Idempotency & timeouts** — Node retries, cancellation, fallback to heuristic on failure (policy from M1 “fail fast” may differ in prod).
- [ ] **D3 — Observability** — Node-level spans/metrics (ties to **C**).

### E — Maven / CI umbrella

- [ ] **E1 — Parent POM (optional)** — Root `pom.xml` packaging `pom` with `<modules>` for `embabel-dice-rca`, `dice-server`, `test-report-server` (and `dice-leap-poc` stays Python, not a module).
- [ ] **E2 — Unified workflow** — `.github/workflows/java-modules.yml`: on push/PR to `main`, run `mvn -pl ... test` or full reactor; **cache** Maven; matrix Java 21.
- [ ] **E3 — Path filters** — Either run full reactor always (slower, simpler) or smart `paths` + `dorny/paths-filter` to skip unchanged modules (team choice).

---

## Goals

1. **End-to-end RCA → QUBO → back** on **realistic** (then real) data paths, behind flags.
2. **Operable Leap** without compromising default CI cost/reliability.
3. **Measurable** production pilots via implemented telemetry, not README-only.
4. **Composable** agent design: solver is a **first-class** step, not a side script.
5. **Maintainable** multi-module Java build and CI visibility.

## Non-goals (for Milestone 2 unless explicitly pulled in)

- Replacing the entire RCA stack with QUBO-only logic.
- On-QPU production workloads (hybrid/Leap API only if chosen).
- Perfect optimality guarantees on messy real graphs.

## Dependencies

- Stable **`SolveRecord`** + `encoding_version` (Milestone 1).
- Agreement on **A3** integration style (subprocess vs service vs JVM port).

## Risks

- Latency and **quota** for Leap; **PII** in logged instances (redaction policy).
- **DAG** complexity: deadlocks, duplicate solver calls, stale context.
- **Maven reactor** time and flaky ITs in `embabel-dice-rca` — umbrella job may need `-DskipITs` profile until stabilized.

## References

- [milestone-1.md](milestone-1.md) — completed PoC scope  
- [dwave.md](../dwave.md) — architecture thesis  
- [dice-leap-poc/README.md](../dice-leap-poc/README.md) — Python runbook  
- [test-report-server/README.md](../test-report-server/README.md) — solver runs UI/API  

---

## Plan evolution

- **2026-01-25:** Milestone 2 drafted from gap analysis (live wiring, metrics implementation, DAG node, Leap CI opt-in, Maven umbrella).
