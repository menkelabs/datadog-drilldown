# Test Report Server

REST API and web UI for test run lookup, analysis, and **running tests from the UI**.

## Run the server

From the **repository root** (so that `../embabel-dice-rca` and H2 paths resolve):

```bash
cd test-report-server
mvn spring-boot:run
```

Or from repo root:

```bash
mvn -f test-report-server/pom.xml spring-boot:run
```

Open **http://localhost:8081** for the UI. The UI lets you:

- **Run tests**: optional pattern (e.g. `AllScenarios`, `DiceRcaIntegration`), optional verbose. Triggers `embabel-dice-rca/run-tests-with-server.sh`, then polls for results.
- View **summary** and **recent runs**.
- **Filter** runs by run ID, scenario, status.
- **View** individual run details (click ID).

## Configuration

| Env / property | Default | Description |
|----------------|---------|-------------|
| `TEST_REPORT_DB_URL` | `jdbc:h2:file:../embabel-dice-rca/test-reports/test-history;...` | H2 URL (same DB as tests). |
| `PROJECT_ROOT` | `..` | Project root (relative to server CWD). Used to find `embabel-dice-rca` and the run script. |
| `run-tests.script-path` | (empty → `embabel-dice-rca/run-tests-with-server.sh`) | Override path to the script. |

When the UI runs tests, the server sets `TEST_RUN_ID` and `TEST_REPORT_DB_PATH` for the script process so results are tagged and stored in the shared H2 DB.

## API

- `GET /api/runs/summaries` — run summaries
- `GET /api/runs?runId=&scenarioId=&status=&limit=` — list runs (optional filters)
- `GET /api/runs/{id}` — run detail by row id
- `GET /api/runs/runId/{runId}` — all rows for a run
- `GET /api/analysis/summary` — global summary
- `POST /api/tests/run` — start a test run (body: `{ "pattern": "", "verbose": false }`)
- `GET /api/tests/run/{runId}/status` — running flag for a run
