# Video narration guide — system architecture & D-Wave integration

## Deliverable intent (read this first)

**Yes — this file is meant to support real videos with voice narration**, not just internal notes.

| Deliverable | What this doc provides |
|-------------|-------------------------|
| **Voice-over script** | Per-chapter **“Narration (example)”** blocks you can read almost verbatim or tighten for time. |
| **Picture / B-roll** | **`[VISUAL]`** lines = what to show while that audio plays (screen capture, diagrams, titles). |
| **Structure** | **Part 1** ≈ architecture + tests + Test Report UI; **Part 2** ≈ D-Wave / QUBO only. Rough **duration hints** on each part. |
| **Shot list** | **Optional appendix — B-roll checklist** at the bottom. |

**Typical outputs:** (a) **one long video** with a clear break between Part 1 and Part 2, or (b) **two videos** — “System overview” and “D-Wave integration deep dive.”

**What you still supply outside this repo:** final pacing (word count → minutes), intro/outro and branding, music / captions policy, recording tools (e.g. OBS + Audacity / Descript), and a **stable demo environment** (mock Datadog vs real keys, whether Leap token appears on screen).

---

Use this document as a **script outline** for recordings. **Part 1** synthesizes the **READMEs** into one overall-architecture story. **Part 2** goes deep **only on the D-Wave / QUBO path** (narration + suggested visuals).

> **Sources:** root [README.md](../README.md), [embabel-dice-rca/README.md](../embabel-dice-rca/README.md), [dice-leap-poc/README.md](../dice-leap-poc/README.md), [test-report-server/README.md](../test-report-server/README.md), [scripts/README.md](../scripts/README.md), [run-integration-tests.sh](../run-integration-tests.sh), [`.github/workflows/`](../.github/workflows/), [dwave.md](../dwave.md), [ADR 0002](adr/0002-dice-leap-subprocess-bridge.md).

---

# Part 1 — Overall architecture (all READMEs)

*Suggested: one video or acts 1–3 of a longer piece (~8–15 minutes).*

## Chapter 1 — Title card: what this repo is

**Narration (example):**

> This project is an AI-assisted **root cause analysis** stack for production incidents. It combines **Datadog telemetry**, an **Embabel** agent for reasoning, and **DICE**—a memory and proposition layer—for durable incident knowledge. Around that core we’ve added tooling for **tests**, **reports**, and an optional **quantum-inspired optimization** path when ranking many competing hypotheses.

**[VISUAL]** Repo root on GitHub; highlight module folders.

---

## Chapter 2 — The four moving parts

**Narration:**

> Think of **four** main pieces, as the READMEs describe them.
>
> First, **`embabel-dice-rca`**—the **RCA agent**. It talks to Datadog for logs, metrics, traces, and events. It scores patterns, proposes root-cause **candidates**, and can drive a chat-style investigation. It’s also the place that **pushes findings toward DICE** and, when enabled, calls the **Python QUBO** pipeline.
>
> Second, **`dice-server`**—the **DICE** service. It ingests text, uses an LLM to extract **propositions**, and answers questions over that knowledge. The RCA module integrates with it over HTTP.
>
> Third, **`test-report-server`**—a **Spring Boot app with a web UI** on port 8081 by default. It stores test runs in **H2**, shows summaries and recent runs, and can **kick off** Maven-backed tests from the browser. It also surfaces **solver runs** from the Python side when JSONL exists—so operators can see QUBO results next to test history.
>
> Fourth, **`dice-leap-poc`**—a **Python** package. It’s not the same as the Kotlin DICE server; it’s the **decision-to-QUBO** lab: JSON **instances**, a **strategy** layer, **local** simulated annealing by default, and **optional D-Wave Leap** when you opt in.

**[VISUAL]** Simple four-box diagram: RCA ↔ Datadog, RCA → DICE server, Test Report UI ↔ H2, dice-leap-poc (Python) as a sidecar to RCA when QUBO is on.

---

## Chapter 3 — Information flow (happy path)

**Narration:**

> In a typical **production-shaped** flow: an operator or webhook steers **incident text and telemetry** into the RCA agent. The agent **pulls evidence** from Datadog—unless you use a **mock profile** for demos without real API keys. Reasoning produces a **report** and **candidates**. Those can be **ingested** into DICE so the team can **query** prior incidents and extracted facts later.
>
> In parallel, **engineering** uses **`mvn test`** from the repo root—there’s a **Maven parent**—and optional **Playwright** tests against the test-report UI. The **QUBO smoke script** at the repo root proves the **Python solver and JVM** can talk without needing Datadog or an LLM.

**[VISUAL]** Sequence: Incident → RCA → Datadog (or mock) → Report → DICE ingest; second lane: Developer → `mvn test` / test-report UI.

---

## Chapter 3a — What tests exist today (inventory)

**Narration:**

> Here’s what actually runs in this repo today, so viewers know what “green CI” means.
>
> **Java — Maven reactor** at the root: **`mvn test`** runs all three modules — **`embabel-dice-rca`**, **`dice-server`**, and **`test-report-server`**. That’s the main gate in **[`.github/workflows/java-modules.yml`](../.github/workflows/java-modules.yml)** on pushes and PRs that touch those paths.
>
> **`embabel-dice-rca`** carries most of the automated surface: **unit-style** tests (e.g. log analysis, scoring), **QUBO / Python bridge** tests (`DiceLeapPythonSolverIntegrationTest`, enricher and rollover planners, JSONL reader), **Dice / scenario** tests (`DiceRcaIntegrationTest`, `AllScenariosTest`, reporting helpers), and **integration** suites that need real services or keys — things like **`SystemIntegrationTest`**, DICE ingestion, chat advice, and end-to-end RCA paths. Some of those are **skipped or environment-gated** unless you set URLs and API keys; the README calls those out.
>
> **`dice-server`** has a **smaller** Kotlin test set — API and service tests.
>
> **`test-report-server`** has **JUnit** coverage for solver-run plumbing (e.g. **`SolverRunsServiceTest`**) plus **browser E2E**: **Playwright** under **`test-report-server/e2e/`** hits the real UI on a high port (**18081** in tests), checks headings, summary and runs panels, the **DICE / QUBO solver runs** table, the **Run tests** form, and a few **REST** endpoints including **actuator health**. CI runs that in **[`test-report-server.yml`](../.github/workflows/test-report-server.yml)** after **`mvn test`** for the module.
>
> **Python — `dice-leap-poc`**: **`pytest tests/`** on **3.10** in **[`dice-leap-poc.yml`](../.github/workflows/dice-leap-poc.yml)**. Default CI is **local classical** only. Tests marked **`leap`** need **`DWAVE_API_TOKEN`** and **`[leap]`** installs — optional workflow when secrets exist.
>
> **Scripts (not always in the same workflows):** **`./scripts/qubo-e2e-smoke.sh`** — venv, toy **`solve_json.py`**, then **`DiceLeapPythonSolverIntegrationTest`** — proves JVM ↔ Python without Datadog. **`./run-integration-tests.sh`** — starts **`dice-server`**, runs **`embabel-dice-rca`** tests with a pattern; expects **`.env`** with keys for full runs.
>
> **Docs:** **`mkdocs build --strict`** in **`docs/documentation-generator/`** via **`docs-site.yml`** when docs paths change.

**[VISUAL]** Table on screen: Workflow → command → scope. Optional: GitHub Actions tab screenshot.

---

## Chapter 3b — Test Report Server: existing GUI & test process

**Narration:**

> The **Test Report** app is the **operator-facing** window into how tests and solver runs look over time.
>
> **Run it** from **`test-report-server`** with the working directory set so **`PROJECT_ROOT`** (default **`..`**) resolves — the server reads an **H2 file** that by default is the **same database** **`embabel-dice-rca`** tests write to under **`test-reports/test-history`**. So Maven test runs that use the reporting hooks **show up** in the UI without extra copy steps.
>
> **What you see on the page** — title **“Test Report – Evaluation”**, a **Run tests** panel, a **Summary** card, **DICE / QUBO solver runs**, and **Recent runs**. You can **filter** by run id, scenario, status; **click** a run for detail. The **Run tests** panel takes an optional **Maven test pattern** (examples in the README: `AllScenarios`, `DiceRcaIntegration`) and a **verbose** flag. **Run** calls **`embabel-dice-rca/run-tests-with-server.sh`**; the server sets **`TEST_RUN_ID`** and **`TEST_REPORT_DB_PATH`** so new rows land in the **shared H2**. The UI **polls** status and can show **log tail** via the API.
>
> **Solver runs** side: **`SolveRecord`** lines can live in **`*.jsonl`** under **`dice-leap-poc/runs`** (configurable). The API can serve **auto** (prefer H2 **`solver_runs`** if populated, else files), **db-only**, or **file-only**; **Sync JSONL → H2** replays files into the DB for consistent browsing. That’s how **QUBO** experiment history sits **next to** classic **JUnit** history in one place.
>
> **Playwright** automates the **happy path visibility** — page loads, panels leave “Loading…”, solver table shows a valid empty or data state, refresh works, run form controls exist, plus **`/api/analysis/summary`**, **`/actuator/health`**, **`/api/solver-runs`**. It does **not** necessarily click “run full integration suite” in CI (that would be long and flaky); it proves the **shell** is wired.

**[VISUAL]** Screen record: `localhost:8081` — pan across four sections; optional **Network** tab showing `/api/runs`, `/api/solver-runs`. Diagram: UI → `POST /api/tests/run` → shell → Maven → H2 → UI poll.

---

## Chapter 4 — How to run it locally (README checklist)

**Narration (short):**

> You need **Java 21** and **Maven**. For real RCA you need an **LLM key** and usually **Datadog** keys—or **`mock-datadog-scenarios`** for rich fake data. **DICE** needs **`dice-server`** running and **`DICE_SERVER_URL`**. The **test-report server** expects paths relative to the repo so H2 and scripts resolve. **Python** for QUBO is only required when you **enable** the bridge and point **`DICE_LEAP_POC_ROOT`** at `dice-leap-poc`.

**[VISUAL]** Bullet checklist on screen; link to root README “Setup”.

---

## Chapter 5 — Close Part 1

**Narration:**

> That’s the **whole system** from the READMEs: RCA plus DICE plus **test observability** — we’ve spelled out **what tests run where**, how the **Test Report UI** ties **H2**, **Maven**, and optional **solver JSONL** together, and where **Playwright** fits. Python QUBO remains an **optional optimizer** in RCA. Next we go **deep on that optimizer**—what it is, when it runs, how **D-Wave Leap** fits, and how to demo it.

**[VISUAL]** Transition title: “D-Wave & QUBO integration”.

---

# Part 2 — D-Wave integration (depth for narration & demos)

*Suggested: dedicated video or acts 4–6 (~10–20 minutes).*

## Chapter 5a — QUBO in one sentence (optional deep-dive)

**Narration (only if the audience asks “what is QUBO?”):**

> **QUBO** is a standard **binary optimization** form: yes or no decisions, a **cost** for combinations, and a **solver** that hunts for a good joint assignment. **Details are secondary** for most viewers—the product story is the **enricher** wiring that output onto the **RCA report** next to **DICE**.

**[VISUAL]** Skip or one slide; prefer time for **5b**.

---

## Chapter 5b — **QuboReportEnricher**: DICE + QUBO integration (lead with this)

**Narration:**

> **Hero of the story:** **`QuboReportEnricher`**. It is how **DICE-side memory** and **QUBO-side optimization** stay on the **same incident thread** instead of feeling like two disconnected tools.
>
> **DICE (`dice-server`)** — on **alert ingest**, **`DiceClient.ingest`** still runs **first**: text in → **propositions** out into DICE memory. **Enabling QUBO does not skip that.**
>
> **RCA (`RcaAgent`)** — then scores **Datadog-backed candidates** and builds the **Report**.
>
> **Enricher** — **after** that report exists, optionally attaches **`findings["qubo"]`**, **strategy / reason**, and on success a **solve summary** plus **recommendations** like a **prioritized short list** that **respects mutual exclusion** between top hypotheses. **Consumers**—operators, UI, chat over the incident—see **one artifact**: **what DICE extracted from narrative** **plus** **what the enricher added from telemetry-shaped optimization**.
>
> **When it runs:** Small cases → **heuristic_only** recorded, **no Python**; larger / more coupled → **`dice-leap-poc`** subprocess (**ADR 0002**). **D-Wave Leap** optional; **local classical** is the default solver story.
>
> **Takeaway:** Pitch the **bridge**, not the matrix math. **`dwave.md`** describes a **conceptual** unified DICE→QUBO pipeline; **this repo** implements the **join** concretely as **ingest + RCA + enricher**.

**[VISUAL]** **Center diagram:** **`dice-server`** (text → propositions) **→** **`RcaAgent`** (telemetry → candidates → report) **→** **`QuboReportEnricher`** **→** enriched **Report** ( **`findings.qubo`** ). **Bold caption:** “Integration point = enricher.” Optional inset: **small** vs **rollover** without numbers unless the room is technical.

---

## Chapter 6 — Concept spec vs this repo (`dwave.md`)

**Narration:**

> **`dwave.md`** is the **long-form architecture vision** (context → strategy → QUBO → local or Leap). **Do not** read it line by line on camera before **Chapter 5b**—viewers will think QUBO is only theory. **In code**, **`dice-server`** handles **ingest / propositions**; **`QuboReportEnricher` + `dice-leap-poc`** handle the **optimization attach** to the **RCA report**. **Chapter 5b** is the accurate **integration** map for demos.

**[VISUAL]** Split screen: left **spec pipeline** (`dwave.md`); right **this repo** with **enricher** highlighted between **DICE** and **solver**.

---

## Chapter 7 — What lives in `dice-leap-poc` (README)

**Narration:**

> The **`dice-leap-poc`** README describes the **Python** implementation of that pipeline slice. You install **`dimod`** and **`dwave-neal`** for local work. **`pip install -e ".[dev,leap]"`** adds **`dwave-system`** when you want **LeapHybridSampler**.
>
> Every run produces a **`SolveRecord`**—JSON with **instance id**, **strategy**, **solver mode**, **objective**, **selected decisions**, timing, and comparison to a **greedy baseline**. **`sample_data/`** holds **small JSON** files—not big telemetry dumps—that **simulate policy**: **no “mountains of data”** for the solver; they encode **how many** hypotheses and **how many** coupling edges, plus **`tier` simple/complex**, so you can show **heuristic ceiling** (e.g. **12×8**), **rollover** (**>12** or **>8** edges), and fixtures **tuned** so SA **beats greedy** for demo payoff.
>
> By default, **CI** uses **`local_classical`** only. **Leap** is **opt-in**: you set **`DWAVE_API_TOKEN`** or run **`dwave setup`**, and an optional GitHub workflow runs **Leap-marked tests** only when a secret exists—so forks don’t break.

**[VISUAL]** Terminal: `solve_json.py` one-line JSON output; table from README: `local_classical` vs `leap_hybrid`.

---

## Chapter 8 — How the JVM uses Python (ADR 0002 + embabel README)

**Narration:**

> We deliberately **did not rewrite** the QUBO compiler in Kotlin for the first integration. **ADR 0002** records the decision: the JVM **subprocess** runs **`dice-leap-poc/scripts/solve_json.py`**. RCA writes an **instance JSON**, the script prints **one JSON line**—the **`SolveRecord`**—and the JVM parses it into **`findings["qubo"]`** when **`embabel.rca.qubo.enabled`** is true.
>
> The **Python interpreter** is configurable; the **README** warns that Maven may not see your **venv**—so smoke tooling exports **`PYTHON`** to the venv binary. For **Leap**, the subprocess **inherits the environment**: if **`DWAVE_API_TOKEN`** is set before **`spring-boot:run`**, the child process can submit to **Leap** when **`solver_mode`** is **`leap_hybrid`**.

**[VISUAL]** Diagram: `RcaAgent` → `DiceLeapPythonSolver` → `ProcessBuilder` → `solve_json.py` → stdout line → `SolveRecord` → report enrichment.

---

## Chapter 9 — When D-Wave actually runs

**Narration:**

> Four levels matter:
>
> 1. **Feature flag** — QUBO enrichment is **off by default** in the JVM (`embabel.rca.qubo.enabled`).
> 2. **Rollover (complexity)** — JVM **`QuboRolloverPlanner`**: **≤12** entities and **≤8** constraint edges → **heuristic_only** (no Python solve); above → **QUBO** path. **`tier: simple` / `complex`** overrides. Aligns with **`dice-leap-poc`** `RolloverConfig`.
> 3. **Solver mode** — After rollover, default **`local_classical`** in **`solve_json.py`**; **`leap_hybrid`** needs **`DWAVE_API_TOKEN`** and **`[leap]`** deps.
> 4. **Failure handling** — Optional **fallback** to top scored candidates if the subprocess errors (`QuboIntegrationProperties`).
>
> So: **D-Wave is never required** for the demo narrative. **Local SA** proves the **full RCA → QUBO → report** path. **Leap** is the **scale / hybrid** story.

**[VISUAL]** Decision tree: QUBO enabled? → `(n, edges)` vs **12 / 8** → call Python? → `local_classical` vs `leap_hybrid` + token?

---

## Chapter 10 — Account setup (for the Leap demo beat)

**Narration:**

> Follow **`docs/DWAVE_LEAP_SETUP`**: create a **Leap** account, get an **API token** from the dashboard or **`dwave setup --auth`**, export **`DWAVE_API_TOKEN`**, install **`[leap]`** extras, verify with **`pytest -m leap`** or **`solve_json.py --solver-mode leap_hybrid`** on the toy fixture. Remind viewers of **quota** and **org policy**—CI keeps Leap **non-required** on purpose.

**[VISUAL]** Leap console → token blur → terminal success line with `solver_mode: leap_hybrid`.

---

## Chapter 11 — Observability (short)

**Narration:**

> When QUBO runs, the stack emits **structured telemetry**—for example **`qubo_telemetry`** log lines and Micrometer timers—so operators can correlate **solver latency** and **outcomes** with incidents. That’s documented in **`QUBO_METRICS_V1`** and ties to the broader pilot plan in **`DWAVE_REAL_WORLD_METRICS`**.

**[VISUAL]** Sample log line on screen; link to metrics doc.

---

## Chapter 12 — One-command smoke (scripts README)

**Narration:**

> For a **tight B-roll** or **CI clip**, run **`./scripts/qubo-e2e-smoke.sh`**. It creates a venv under **`dice-leap-poc`**, installs the package, runs the toy solve, then runs the **JVM integration test**—proving the **bridge** without Datadog or an LLM. Add **`--verbose`** when you need log footage for debugging scenes.

**[VISUAL]** Split terminal: Python JSON line + Maven “BUILD SUCCESS”.

---

## Chapter 13 — Closing (D-Wave story)

**Narration:**

> To summarize the **D-Wave** story in this repo: we **encode** ranked RCA candidates as a **structured decision instance**, **compile** to **QUBO**, **solve** with **classical** code by default or **Leap hybrid** when you choose, and **fold** the **SolveRecord** back into the **RCA report**. DICE and Datadog stay the **context** fabric; **D-Wave** is the **optional engine** for hard **discrete choice** under constraints—not a replacement for monitoring or LLM reasoning.

**[VISUAL]** Final diagram: Datadog + Embabel + DICE + (optional) QUBO → Local | Leap.

---

## Optional appendix — B-roll checklist

| Shot | Where |
|------|--------|
| Four modules in IDE | Repo tree |
| `mvn test` green | Root |
| GitHub Actions: java-modules + test-report-server + dice-leap-poc | `.github/workflows/` |
| Test report UI (Summary, Recent runs, Run tests, Solver runs) | `localhost:8081` |
| Playwright headed run (optional) | `test-report-server` → `npm run e2e:test` |
| H2 path / `test-reports` folder | `embabel-dice-rca/test-reports` |
| `solve_json.py` output | `dice-leap-poc` |
| `qubo-e2e-smoke.sh` | Repo root |
| Leap token (blurred) + hybrid run | User env only |

---

*Document version: aligned with READMEs + workflows as of 2026-01-24. Update when modules, tests, or CI jobs change.*
