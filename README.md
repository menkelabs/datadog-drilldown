# Datadog RCA Assistant (Embabel + DICE)

AI-powered **Root Cause Analysis (RCA)** for production incidents using **Embabel** agents and **DICE** (Domain-Integrated Context Engineering) for memory and reasoning.

---

## D-Wave & QUBO (highlight)

Decision problems can be compiled to **QUBO** and solved **locally** (simulated annealing) or, optionally, on **D-Wave Leap** cloud hybrid samplers. This stack is optional for core RCA; it enriches ranking when enabled.

| What | Where |
|------|--------|
| **Leap API token — obtain & configure** | **[docs/DWAVE_LEAP_SETUP.md](docs/DWAVE_LEAP_SETUP.md)** — account, `DWAVE_API_TOKEN`, `pip install ".[leap]"`, verify with pytest or `solve_json.py --solver-mode leap_hybrid` |
| **Python PoC** (instances → QUBO → solve) | **[dice-leap-poc/](dice-leap-poc/)** · spec **[dwave.md](dwave.md)** · milestones **[milestones/milestone-1.md](milestones/milestone-1.md)** |
| **JVM bridge** (RCA → Python subprocess) | Flagged in **`embabel-dice-rca`** — **[docs/adr/0002-dice-leap-subprocess-bridge.md](docs/adr/0002-dice-leap-subprocess-bridge.md)** · **[embabel-dice-rca/README.md](embabel-dice-rca/README.md)** (QUBO section) |
| **One-shot smoke** (venv + toy solve + JVM IT) | **`./scripts/qubo-e2e-smoke.sh`** · **[scripts/README.md](scripts/README.md)** |
| **Telemetry** | **[docs/QUBO_METRICS_V1.md](docs/QUBO_METRICS_V1.md)** · pilot plan **[docs/DWAVE_REAL_WORLD_METRICS.md](docs/DWAVE_REAL_WORLD_METRICS.md)** |
| **Milestone status** | **[milestones/milestone-2.md](milestones/milestone-2.md)** (M2a–M2d) |

**Default path:** no Leap account required — local classical solver in `dice-leap-poc`. **Leap** is opt-in (free/trial per D-Wave).

---

## Test Report Server (WIP)

Web UI for running tests, viewing results, and tuning AI parameters. Screenshot: `docs/img/refactor-fe.png`.

Details: **[test-report-server/README.md](test-report-server/README.md)** · Playwright E2E: `test-report-server/e2e/` (`npm run e2e:ci` from **`test-report-server/`**).

---

## Architecture

Two primary Kotlin / Spring Boot modules:

1. **`embabel-dice-rca`** — Analysis engine and Embabel agent: Datadog telemetry, pattern analysis, root-cause candidates, optional QUBO enrichment, DICE ingest.
2. **`dice-server`** — Proposition extraction and reasoning API over incident memory.

Additional: **`test-report-server`** — REST + static UI for test runs, solver JSONL, and analysis.

---

## Modules (summary)

| Module | Role |
|--------|------|
| **embabel-dice-rca** | Datadog REST, log/metric/trace analysis, Embabel workflow, DICE bridge, optional **mock Datadog** profiles for local dev |
| **dice-server** | Ingestion, LLM proposition extraction, semantic query |
| **test-report-server** | H2-backed run history, UI, optional test triggers, DICE/QUBO solver run views |
| **dice-leap-poc** | Python: instance JSON → QUBO → `local_classical` or **`leap_hybrid`** |

---

## Setup

### Prerequisites

- Java **21+**, Maven **3.8+**
- LLM key (OpenAI / Anthropic / Ollama) for agents
- Datadog keys **or** Spring profile **`mock-datadog`** / **`mock-datadog-scenarios`** (see **[embabel-dice-rca/README.md](embabel-dice-rca/README.md)**)

### Build & test (reactor)

From **repo root**:

```bash
mvn test
```

Aggregator: **[pom.xml](pom.xml)**. Single module: `cd embabel-dice-rca && mvn test`, etc.

- **Branch protection / CI:** **[docs/CI_BRANCH_PROTECTION.md](docs/CI_BRANCH_PROTECTION.md)**
- **QUBO smoke:** `./scripts/qubo-e2e-smoke.sh` (optional `--verbose`)

### Environment (typical)

```bash
export OPENAI_API_KEY="sk-..."
export DD_API_KEY="..."
export DD_APP_KEY="..."
export DD_SITE="datadoghq.com"
```

### Run services

```bash
cd dice-server && mvn spring-boot:run
cd embabel-dice-rca && mvn spring-boot:run
```

### IDE (VS Code)

Java/Kotlin **21** — see **[.vscode/settings.json](.vscode/settings.json)**. If the IDE shows JVM target mismatches, reload the window and point the project SDK at Java 21; Maven `pom.xml` files use `jvmTarget=21`.

---

## Testing

| Kind | Command / pointer |
|------|-------------------|
| Full Java reactor | `mvn test` (root) |
| Integration (DICE + env) | `cd embabel-dice-rca && mvn test -Dtest=SystemIntegrationTest` (needs **`DICE_SERVER_URL`**, keys, etc.) |
| Test Report UI E2E | `cd test-report-server && npm run e2e:ci && npm run e2e:browsers && npm run e2e:test` |
| QUBO Python + JVM IT | `./scripts/qubo-e2e-smoke.sh` |

Tuning workflow and H2 details: **[test-report-server/README.md](test-report-server/README.md)** · SQL / analysis: **[embabel-dice-rca/docs/TEST_REPORT_ANALYSIS.md](embabel-dice-rca/docs/TEST_REPORT_ANALYSIS.md)**.

---

## Documentation

| Doc | Topic |
|-----|--------|
| **[docs/DWAVE_LEAP_SETUP.md](docs/DWAVE_LEAP_SETUP.md)** | D-Wave Leap token & venv |
| **[dwave.md](dwave.md)** | DICE + QUBO + Leap architecture |
| **[docs/adr/0002-dice-leap-subprocess-bridge.md](docs/adr/0002-dice-leap-subprocess-bridge.md)** | JVM ↔ Python bridge |
| **[embabel-dice-rca/docs/architecture/README.md](embabel-dice-rca/docs/architecture/README.md)** | Diagrams |
| **[docs/puml-setup.md](docs/puml-setup.md)** | PlantUML |
| **[milestones/milestone-1.md](milestones/milestone-1.md)** · **[milestone-2.md](milestones/milestone-2.md)** | Roadmap |
