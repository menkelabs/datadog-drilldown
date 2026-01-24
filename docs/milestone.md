# Milestones

Tracked items from session work on datadog-drilldown (embabel-dice-rca, dice-server).

---

## 1. Re-enable SystemIntegrationTest

- **Status:** Not started  
- **Context:** `SystemIntegrationTest` is excluded from `run-tests-with-server.sh` because it hangs after Spring Boot context load (`Started SystemIntegrationTest in 2.036 seconds` then no further output).
- **Goal:** Fix the hang (e.g. bean init, HTTP client, or test lifecycle) and remove the exclusion so the script runs it again.

---

## 2. Get reporting working on all scenario tests

- **Status:** Not started  
- **Context:** Test execution reporting (JSON/Markdown/HTML) exists (`TestExecutionReport`, `TestReportGenerator`, `TestReportCollector`, `runTestWithReporting`), but scenario tests don’t use it yet.
- **Goal:** Wire reporting into `AllScenariosTest` (and any other scenario-style tests) so every run produces reports in `test-reports/` for all scenario tests.

---

## 3. Determine cause for AllScenariosTest failures

- **Status:** Not started  
- **Context:** `AllScenariosTest` has multiple failures (e.g. “scenario - kafka consumer lag” and others). Failures are `AssertionFailedError` on `verification.passed` (keyword coverage / expected root cause).
- **Goal:** Identify root cause(s): LLM output vs. expected keywords, coverage threshold, or scenario design. Use improved failure logging and, if needed, reports from milestone 2.

---

*Last updated: 2026-01-23*
