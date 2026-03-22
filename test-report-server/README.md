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

## UI / API E2E tests (Playwright)

Browser smoke tests live under **[e2e/](e2e/)** (`package-lock.json` is **only** there). They start Spring Boot on port **18081** (via `SERVER_PORT`) and assert the main page, panels, and a few REST endpoints.

**Prerequisites:** JDK 21, Maven, Node 20+.

**Do not run `npm ci` in `test-report-server/` alone** — it has no lockfile. Either `cd e2e` first, or use the npm scripts below from `test-report-server/`.

```bash
cd test-report-server/e2e
npm ci
npx playwright install chromium   # first time only
npm test
```

Or from **`test-report-server/`** (parent of `e2e/`):

```bash
npm run e2e:ci              # same as npm ci inside e2e/
npm run e2e:browsers        # install Chromium for Playwright (first time / after upgrade)
npm run e2e:test            # playwright test
```

CI runs the same suite in [`.github/workflows/test-report-server.yml`](../.github/workflows/test-report-server.yml) (`e2e-ui` job after `mvn test`).

## Configuration

| Env / property | Default | Description |
|----------------|---------|-------------|
| `TEST_REPORT_DB_URL` | `jdbc:h2:file:../embabel-dice-rca/test-reports/test-history;...` | H2 URL (same DB as tests). |
| `PROJECT_ROOT` | `..` | Project root (relative to server CWD). Used to find `embabel-dice-rca` and the run script. |
| `run-tests.script-path` | (empty → `embabel-dice-rca/run-tests-with-server.sh`) | Override path to the script. |
| `SOLVER_RUNS_DIR` / `solver-runs.directory` | `../dice-leap-poc/runs` | Directory of `*.jsonl` from [dice-leap-poc](../dice-leap-poc/README.md) (`SolveRecord` lines). |

When the UI runs tests, the server sets `TEST_RUN_ID` and `TEST_REPORT_DB_PATH` for the script process so results are tagged and stored in the shared H2 DB.

## API

### DICE / QUBO solver runs (JSONL + H2)

- `GET /api/solver-runs?limit=500&source=auto|db|file` — **`auto`** (default): read H2 `solver_runs` if non-empty, else JSONL files. **`db`**: H2 only. **`file`**: JSONL only.
- `POST /api/solver-runs/sync` — body `{}`. Truncates `solver_runs` and re-imports every line from all `*.jsonl` under `solver-runs.directory`. Returns `{ "inserted": N }`.

**UI:** “DICE / QUBO solver runs” card — refresh, source selector, **Sync JSONL → H2**. **Reset DB** (admin) also `DELETE`s `solver_runs`.

### Test runs (H2)

- `GET /api/runs/summaries` — run summaries
- `GET /api/runs?runId=&scenarioId=&status=&limit=` — list runs (optional filters)
- `GET /api/runs/{id}` — run detail by row id
- `GET /api/runs/runId/{runId}` — all rows for a run
- `GET /api/analysis/summary` — global summary
- `GET /api/analysis/suggestions` — latest and all `run-*-analysis.md` / `analysis-suggestions-*.md` contents
- `POST /api/tests/run` — start a test run (body: `{ "pattern": "", "verbose": false }`)
- `GET /api/tests/run/{runId}/status` — running flag for a run
- `GET /api/tests/run/{runId}/log?tailLines=500` — tail of test run log

**Admin (database & logs):**

- `POST /api/admin/reset-db` — body `{ "clearLogs": boolean }`. Delete all `test_runs`; if `clearLogs`, also delete all `test-run-*.log` and analysis files (`run-*-analysis.md`, `analysis-suggestions-*.md`) in test-reports.
- `POST /api/admin/purge-before` — body `{ "before": "ISO8601", "clearLogs": boolean }`. Delete rows with `started_at < before`; if `clearLogs`, also delete log and analysis files with `lastModified < before`.
- `POST /api/admin/clear-logs` — body `{ "before": "ISO8601" | null }`. Delete `test-run-*.log` and analysis files; if `before` set, only those with `lastModified < before`.
