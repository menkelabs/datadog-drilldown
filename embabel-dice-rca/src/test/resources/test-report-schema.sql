-- Persistent store for test execution reports (H2).
-- Used by embabel-dice-rca tests and by test-report-server for lookup/analysis.
--
-- Design for data analysis:
-- - First-class columns for dimensions (filter, GROUP BY): scenario, status, ai_model, ai_temperature
-- - Numeric metrics for aggregations: duration_ms, keyword_coverage, propositions_extracted
-- - Verification flags for failure analysis: component_identified, cause_type_identified
-- - Indexes on common analysis axes: run_id, scenario+passed, started_at, ai params

CREATE TABLE IF NOT EXISTS test_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  test_name VARCHAR(512) NOT NULL,
  test_class VARCHAR(512) NOT NULL,
  scenario_id VARCHAR(256),
  context_id VARCHAR(256),
  status VARCHAR(16) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

  -- Dimensions (filter, group by)
  alert_service VARCHAR(256),
  alert_severity VARCHAR(64),
  expected_component VARCHAR(256),
  expected_cause_type VARCHAR(256),

  -- AI params (tuning analysis: correlate model/temperature with pass rate)
  ai_model VARCHAR(128),
  ai_temperature DOUBLE,

  -- Numeric metrics (aggregations, percentiles, trends)
  duration_ms BIGINT,
  keyword_coverage DOUBLE,
  required_keyword_coverage DOUBLE,
  keywords_found_count INT,
  keywords_missing_count INT,
  propositions_extracted INT,
  analysis_duration_ms BIGINT,
  prior_knowledge_load_ms BIGINT,
  dice_api_calls INT,
  datadog_api_calls INT,

  -- Verification flags (failure analysis: component vs cause_type vs coverage)
  passed BOOLEAN NOT NULL,
  component_identified BOOLEAN,
  cause_type_identified BOOLEAN,

  -- Detail (drill-down, full report)
  error_message CLOB,
  actual_root_cause CLOB,
  expected_keywords CLOB,
  ai_params_json CLOB,
  report_json CLOB NOT NULL
);

-- Indexes for common analysis queries
CREATE INDEX IF NOT EXISTS idx_test_runs_run_id ON test_runs(run_id);
CREATE INDEX IF NOT EXISTS idx_test_runs_scenario ON test_runs(scenario_id);
CREATE INDEX IF NOT EXISTS idx_test_runs_status ON test_runs(status);
CREATE INDEX IF NOT EXISTS idx_test_runs_started ON test_runs(started_at);
CREATE INDEX IF NOT EXISTS idx_test_runs_test_class ON test_runs(test_class);
CREATE INDEX IF NOT EXISTS idx_test_runs_passed ON test_runs(passed);
CREATE INDEX IF NOT EXISTS idx_test_runs_ai_model ON test_runs(ai_model);
CREATE INDEX IF NOT EXISTS idx_test_runs_ai_temperature ON test_runs(ai_temperature);
-- Composite: scenario + outcome (e.g. pass rate by scenario)
CREATE INDEX IF NOT EXISTS idx_test_runs_scenario_passed ON test_runs(scenario_id, passed);
-- Composite: run + status (e.g. summary per run)
CREATE INDEX IF NOT EXISTS idx_test_runs_run_status ON test_runs(run_id, status);
