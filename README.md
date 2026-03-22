# Datadog RCA Assistant (Embabel + DICE)

AI-assisted **root cause analysis** for production incidents using **Embabel** agents and **DICE** (Domain-Integrated Context Engineering) for memory and reasoning.

## Quick links

| What | Where |
|------|--------|
| **Demo videos (GitHub Pages)** | **[Live site](https://menkelabs.github.io/datadog-drilldown/)** · [workflow](https://github.com/menkelabs/datadog-drilldown/actions/workflows/video-docs-pages.yml) — set **Settings → Pages → Source: GitHub Actions** or deploy returns **404**; committed `recordings/*.mp4` only |
| **Video bundle** (TTS, text visuals, compose) | [`docs/documentation-generator/datadog-drilldown-video-docs/`](docs/documentation-generator/datadog-drilldown-video-docs/) |
| **Narrative script** (architecture + D-Wave) | [`docs/VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md`](docs/VIDEO_NARRATIVE_ARCHITECTURE_AND_DWAVE.md) |
| **D-Wave Leap** (token, venv, verify) | [`docs/DWAVE_LEAP_SETUP.md`](docs/DWAVE_LEAP_SETUP.md) |
| **QUBO spec & milestones** | [`dwave.md`](dwave.md) · [`milestones/milestone-1.md`](milestones/milestone-1.md), [`milestone-2.md`](milestones/milestone-2.md) |
| **JVM ↔ Python bridge (ADR)** | [`docs/adr/0002-dice-leap-subprocess-bridge.md`](docs/adr/0002-dice-leap-subprocess-bridge.md) |
| **CI / branch protection** | [`docs/CI_BRANCH_PROTECTION.md`](docs/CI_BRANCH_PROTECTION.md) |
| **QUBO e2e smoke** | `./scripts/qubo-e2e-smoke.sh` · [`scripts/README.md`](scripts/README.md) |

If you fork the repo, replace `menkelabs/datadog-drilldown` in the URLs above with your fork.

## D-Wave & QUBO (optional)

Problems can be compiled to **QUBO** and solved **locally** or, optionally, on **D-Wave Leap**. Optional for core RCA; enriches ranking when enabled.

| Topic | Doc / path |
|--------|------------|
| Leap API token & config | [`docs/DWAVE_LEAP_SETUP.md`](docs/DWAVE_LEAP_SETUP.md) |
| Python PoC (instance → QUBO → solve) | [`dice-leap-poc/`](dice-leap-poc/) · [`dwave.md`](dwave.md) |
| JVM bridge & RCA flag | [`embabel-dice-rca/README.md`](embabel-dice-rca/README.md) (QUBO section) |
| Telemetry / metrics | [`docs/QUBO_METRICS_V1.md`](docs/QUBO_METRICS_V1.md) · [`docs/DWAVE_REAL_WORLD_METRICS.md`](docs/DWAVE_REAL_WORLD_METRICS.md) |

Default path needs **no** Leap account — local classical solver in `dice-leap-poc`.

## Architecture

- **`embabel-dice-rca`** — Analysis engine: Datadog telemetry, Embabel workflow, candidates, optional QUBO enrichment, DICE ingest.
- **`dice-server`** — Proposition extraction and reasoning over incident memory.
- **`test-report-server`** — REST + UI for test runs, solver JSONL, analysis (see [`test-report-server/README.md`](test-report-server/README.md)).

## Modules (summary)

| Module | Role |
|--------|------|
| `embabel-dice-rca` | Datadog REST, logs/metrics/traces, Embabel, DICE, optional mock Datadog profiles |
| `dice-server` | Ingestion, LLM propositions, semantic query |
| `test-report-server` | H2 run history, UI, optional test triggers, solver run views |
| `dice-leap-poc` | Python: instance JSON → QUBO → `local_classical` or `leap_hybrid` |

## Setup

**Prerequisites:** Java **21+**, Maven **3.8+**; LLM key for agents; Datadog keys **or** Spring profile **`mock-datadog`** / **`mock-datadog-scenarios`** (see [`embabel-dice-rca/README.md`](embabel-dice-rca/README.md)).

**Build & test (reactor root):**

```bash
mvn test
```

Single module: `cd embabel-dice-rca && mvn test` (etc.). Aggregator: [`pom.xml`](pom.xml).

**Typical env:**

```bash
export OPENAI_API_KEY="sk-..."
export DD_API_KEY="..."
export DD_APP_KEY="..."
export DD_SITE="datadoghq.com"
```

**Run services:**

```bash
cd dice-server && mvn spring-boot:run
cd embabel-dice-rca && mvn spring-boot:run
```

**VS Code / IDE:** Java **21** — [`.vscode/settings.json`](.vscode/settings.json).

## Testing

| Kind | Command / pointer |
|------|-------------------|
| Full Java reactor | `mvn test` (root) |
| DICE integration | `cd embabel-dice-rca && mvn test -Dtest=SystemIntegrationTest` (needs `DICE_SERVER_URL`, keys, …) |
| Test Report UI E2E | `cd test-report-server && npm run e2e:ci` (see e2e README) |
| QUBO Python + JVM | `./scripts/qubo-e2e-smoke.sh` |

Tuning / H2 / analysis: [`test-report-server/README.md`](test-report-server/README.md) · [`embabel-dice-rca/docs/TEST_REPORT_ANALYSIS.md`](embabel-dice-rca/docs/TEST_REPORT_ANALYSIS.md).

## Documentation index

| Doc | Topic |
|-----|--------|
| [`docs/documentation-generator/`](docs/documentation-generator/) | Shared **video-framework** templates + **datadog-drilldown-video-docs** bundle |
| [`docs/DWAVE_LEAP_SETUP.md`](docs/DWAVE_LEAP_SETUP.md) | Leap token & venv |
| [`dwave.md`](dwave.md) | DICE + QUBO + Leap architecture |
| [`docs/adr/0002-dice-leap-subprocess-bridge.md`](docs/adr/0002-dice-leap-subprocess-bridge.md) | Subprocess bridge |
| [`embabel-dice-rca/docs/architecture/README.md`](embabel-dice-rca/docs/architecture/README.md) | Diagrams |
| [`docs/puml-setup.md`](docs/puml-setup.md) | PlantUML |

*Optional MkDocs:* if you add `mkdocs.yml` under [`docs/documentation-generator/`](docs/documentation-generator/), workflow **`docs-site`** will run `mkdocs build --strict` again; otherwise it skips with a notice.
