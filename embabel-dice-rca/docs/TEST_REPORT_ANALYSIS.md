# Test Report Schema & Data Analysis

Test execution data is stored in a local H2 database (`test-reports/test-history`) and is designed for **data analysis**: understanding why tests fail, correlating outcomes with AI params, and tuning scenarios.

## Schema Overview

The `test_runs` table includes:

| Column | Type | Purpose |
|--------|------|---------|
| `run_id` | VARCHAR | Groups tests by mvn run; filter by run |
| `scenario_id` | VARCHAR | Scenario name (e.g. `redis-cache-failure`); group pass rate by scenario |
| `status` | VARCHAR | PASSED, FAILED, ERROR, SKIPPED |
| `passed` | BOOLEAN | Quick filter for outcome |
| `ai_model` | VARCHAR | LLM model used; correlate with pass rate |
| `ai_temperature` | DOUBLE | Temperature; tune and compare |
| `keyword_coverage` | DOUBLE | 0–1; metric for verification strength |
| `keywords_found_count`, `keywords_missing_count` | INT | Drill into verification failures |
| `component_identified`, `cause_type_identified` | BOOLEAN | Verify component vs cause-type hits |
| `duration_ms`, `analysis_duration_ms`, `prior_knowledge_load_ms` | BIGINT | Performance metrics |
| `propositions_extracted` | INT | Prior-knowledge load size |
| `expected_component`, `expected_cause_type` | VARCHAR | Expected vs actual analysis |
| `report_json` | CLOB | Full report for drill-down |

## Indexes

Indexes support common analysis patterns:

- `run_id`, `scenario_id`, `status`, `started_at`, `test_class`, `passed`
- `ai_model`, `ai_temperature` — param sweeps
- `(scenario_id, passed)` — pass rate by scenario
- `(run_id, status)` — summary per run

## Example Analysis Queries

Run these via the **test-report-server** API or directly against H2 (e.g. `java -jar h2*.jar` pointed at `test-reports/test-history`).

### Pass rate by scenario

```sql
SELECT scenario_id,
       COUNT(*) AS total,
       SUM(CASE WHEN passed THEN 1 ELSE 0 END) AS passed,
       ROUND(100.0 * SUM(CASE WHEN passed THEN 1 ELSE 0 END) / COUNT(*), 1) AS pass_pct
FROM test_runs
WHERE scenario_id IS NOT NULL
GROUP BY scenario_id
ORDER BY pass_pct ASC, total DESC;
```

### Pass rate by AI model / temperature

```sql
SELECT ai_model, ai_temperature,
       COUNT(*) AS total,
       SUM(CASE WHEN passed THEN 1 ELSE 0 END) AS passed,
       ROUND(100.0 * SUM(CASE WHEN passed THEN 1 ELSE 0 END) / COUNT(*), 1) AS pass_pct
FROM test_runs
WHERE ai_model IS NOT NULL
GROUP BY ai_model, ai_temperature
ORDER BY ai_model, ai_temperature;
```

### Failures: coverage vs component vs cause-type

```sql
SELECT scenario_id, test_name, status,
       keyword_coverage, keywords_found_count, keywords_missing_count,
       component_identified, cause_type_identified,
       LEFT(actual_root_cause, 200) AS actual_root_cause_preview
FROM test_runs
WHERE passed = FALSE
ORDER BY started_at DESC;
```

### Recent runs summary

```sql
SELECT run_id, MIN(started_at) AS run_start,
       COUNT(*) AS total,
       SUM(CASE WHEN passed THEN 1 ELSE 0 END) AS passed,
       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
       SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS errors
FROM test_runs
GROUP BY run_id
ORDER BY run_start DESC
LIMIT 20;
```

### Slow tests (duration)

```sql
SELECT scenario_id, test_name, duration_ms, analysis_duration_ms, prior_knowledge_load_ms
FROM test_runs
WHERE duration_ms IS NOT NULL
ORDER BY duration_ms DESC
LIMIT 20;
```

### Low keyword coverage (verification weak)

```sql
SELECT scenario_id, test_name, keyword_coverage, required_keyword_coverage,
       keywords_found_count, keywords_missing_count, actual_root_cause
FROM test_runs
WHERE keyword_coverage IS NOT NULL AND keyword_coverage < required_keyword_coverage
ORDER BY keyword_coverage ASC;
```

## Test result analyzer & param suggestions

`TestResultAnalyzer` runs over collected reports and produces **findings** plus **param adjustment suggestions**:

- **Keyword coverage**: If failures often sit just below threshold → suggest lowering `required_keyword_coverage` or adding synonyms.
- **Missing keywords**: Aggregate `keywords_missing` across failures → suggest adding expected keywords or synonyms.
- **Component / cause-type**: If `component_identified` or `cause_type_identified` often false → suggest aligning expected labels with LLM wording.
- **Temperature**: When `ai_temperature` is present, compare fail vs pass → suggest lower temperature (e.g. 0.0–0.2) if failures correlate with higher temp.
- **Scenario pass rate**: Per-scenario pass rate &lt; 50% → suggest reviewing keywords and expected root cause.
- **Duration**: High analysis duration (e.g. p95 &gt; 30s) → suggest simplifying prior knowledge or prompts.

Reports:

- `generateReports()` writes **`<runId>-analysis.md`** (e.g. `run-1737701234567-abc123-analysis.md`): findings + suggestion table + reasons. The runId is `TEST_RUN_ID` when set (e.g. runs started from the test-report-server UI) or `run-<epoch>`, so **runs, logs, and analysis share the same key**. Legacy `analysis-suggestions-YYYYMMDD-HHmmss.md` files are still supported.
- The analysis report also includes a **Per-test: actual LLM output vs expected** section: for each test, **expected** (keywords, component, cause type, required coverage) and **actual LLM output** (root cause analysis), plus a brief verification summary (coverage, found/missing keywords, component/cause-type).
- `generateSummary()` appends a short “Suggested param adjustments” section to the summary file when there are suggestions.

Use `TestReportCollector.runAnalysis()` to obtain `AnalysisResult` (findings + suggestions) programmatically for the test-report-server or other tooling.

## Reconciliation: test engine vs AI knobs

**What the test engine tracks:** The results DB and `TestResultAnalyzer` are designed around `ai_model`, `ai_temperature`, and `scenario_id`. They support pass-rate-by-scenario, model/temperature sweeps, and suggestions (e.g. lower temperature if failures correlate with higher temp).

**What the code uses:**
- **Integration tests** (`DiceRcaIntegrationTest`) call DICE over HTTP. All LLM work runs in **dice-server** (ingest extraction, query reasoning). Dice-server uses a fixed model (`OpenAiModels.GPT_41_NANO`) and does not expose temperature.
- **RCA agent** (e.g. interactive mode) uses `RcaAgentProperties.analysisModel` and Spring AI chat options (model, temperature). That path is separate from the integration-test pipeline.

**Current status:**
- **Scenario / AI params in reports:** Integration tests now set `scenario_id` and `ai_params` (model, temperature) via `withMetadata` so H2 and the analyzer receive them. Model/temperature reflect the DICE pipeline (currently fixed; temperature stored as placeholder for future tuning).
- **Tuning knobs:** Dice-server model (and eventually temperature) would need to be configurable for real param sweeps. The test-report-server UI “parameter override” and “what-if” analysis are planned (see session-notes).

**Bottom line:** The test engine is looking at the right knobs (model, temperature, scenario, keywords). Tests now populate them. Param sweeps and “what-if” runs will be actionable once DICE exposes configurable model/temperature.

## Populating AI params

Set `metadata["ai_params"]` in your test report (e.g. via `withMetadata("ai_params", mapOf("model" to "gpt-4", "temperature" to 0.2))`) or use `metadata["ai_model"]` / `metadata["ai_temperature"]` so `ai_model` and `ai_temperature` are stored. The test-report-server and the above queries can then use them for analysis.

## DB location

- **Tests**: `./test-reports/test-history` (relative to `embabel-dice-rca`), or `TEST_REPORT_DB_PATH`.
- **test-report-server**: Same path; use `TEST_REPORT_DB_PATH` when running from another CWD.

The H2 file (`test-history.mv.db`) is created on first `addReport`. If the schema changes, delete the file and re-run tests to recreate the table.
