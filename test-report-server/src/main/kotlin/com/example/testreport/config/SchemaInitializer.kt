package com.example.testreport.config

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

@Component
class SchemaInitializer(private val jdbc: JdbcTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        val ddl = """
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
              alert_service VARCHAR(256),
              alert_severity VARCHAR(64),
              expected_component VARCHAR(256),
              expected_cause_type VARCHAR(256),
              ai_model VARCHAR(128),
              ai_temperature DOUBLE,
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
              passed BOOLEAN NOT NULL,
              component_identified BOOLEAN,
              cause_type_identified BOOLEAN,
              error_message CLOB,
              actual_root_cause CLOB,
              expected_keywords CLOB,
              ai_params_json CLOB,
              report_json CLOB NOT NULL
            )
        """.trimIndent()
        try {
            jdbc.execute(ddl)
            log.info("test_runs schema initialized")
        } catch (e: Exception) {
            log.warn("Schema init skipped: {}", e.message)
        }
    }
}
