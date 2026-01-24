package com.example.rca.dice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Persistent H2 store for test execution reports. Each [addReport] appends a row;
 * data survives across test classes (single JVM with forkCount=1) and is available
 * for the test-report-server to query for lookup and analysis.
 *
 * Schema is designed for data analysis: first-class columns for AI params, metrics,
 * and verification flags; indexes on run_id, scenario, status, passed, ai_model, etc.
 *
 * DB path: [TEST_REPORT_DB_PATH] or `./test-reports/test-history` (relative to CWD).
 */
class H2TestReportStore(
    private val dbPath: String = System.getenv("TEST_REPORT_DB_PATH")
        ?: Paths.get("test-reports", "test-history").toString()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.INDENT_OUTPUT)

    private val runIdRef = AtomicReference<String?>(null)

    private fun ensureRunId(): String {
        return runIdRef.updateAndGet { current ->
            current ?: (System.getenv("TEST_RUN_ID") ?: "run-${Instant.now().epochSecond}")
        }!!
    }

    private fun jdbcUrl(): String {
        val path = File(dbPath).absolutePath
        // Use AUTO_SERVER=TRUE to allow multiple connections (test-report-server + tests)
        return "jdbc:h2:file:$path;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
    }

    private fun ensureSchema(conn: Connection) {
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
            );
        """.trimIndent()
        conn.createStatement().execute(ddl)
        val indexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_test_runs_run_id ON test_runs(run_id)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_scenario ON test_runs(scenario_id)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_status ON test_runs(status)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_started ON test_runs(started_at)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_test_class ON test_runs(test_class)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_passed ON test_runs(passed)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_ai_model ON test_runs(ai_model)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_ai_temperature ON test_runs(ai_temperature)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_scenario_passed ON test_runs(scenario_id, passed)",
            "CREATE INDEX IF NOT EXISTS idx_test_runs_run_status ON test_runs(run_id, status)"
        )
        indexes.forEach { sql ->
            try {
                conn.createStatement().execute(sql)
            } catch (e: Exception) {
                if (!e.message.orEmpty().contains("already exists", ignoreCase = true)) {
                    logger.debug("Index creation skipped: $sql — ${e.message}")
                }
            }
        }
    }

    /** Persist a single report. Safe to call from multiple threads. */
    fun addReport(report: TestExecutionReport) {
        val runId = ensureRunId()
        val dir = File(dbPath).absoluteFile.parentFile
        dir?.mkdirs()

        DriverManager.getConnection(jdbcUrl(), "sa", "").use { conn ->
            ensureSchema(conn)
            val scenarioId = report.metadata["scenario_id"]?.toString()
            val aiParams = report.metadata["ai_params"]
            val aiParamsJson = if (aiParams != null) objectMapper.writeValueAsString(aiParams) else null
            val aiModel = (aiParams as? Map<*, *>)?.get("model")?.toString() ?: report.metadata["ai_model"]?.toString()
            val aiTemp = (aiParams as? Map<*, *>)?.get("temperature")?.let { (it as? Number)?.toDouble() }
                ?: (report.metadata["ai_temperature"] as? Number)?.toDouble()
            val expectedKw = report.verification?.expectedKeywords
            val expectedKeywordsJson = if (expectedKw != null) objectMapper.writeValueAsString(expectedKw) else null
            val reportJson = objectMapper.writeValueAsString(report)

            val sql = """
                INSERT INTO test_runs (
                  run_id, test_name, test_class, scenario_id, context_id, status,
                  started_at, alert_service, alert_severity, expected_component, expected_cause_type,
                  ai_model, ai_temperature, duration_ms, keyword_coverage, required_keyword_coverage,
                  keywords_found_count, keywords_missing_count, propositions_extracted,
                  analysis_duration_ms, prior_knowledge_load_ms, dice_api_calls, datadog_api_calls,
                  passed, component_identified, cause_type_identified,
                  error_message, actual_root_cause, expected_keywords, ai_params_json, report_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { ps ->
                var i = 1
                ps.setString(i++, runId)
                ps.setString(i++, report.testName)
                ps.setString(i++, report.testClass)
                ps.setString(i++, scenarioId)
                ps.setString(i++, report.contextId)
                ps.setString(i++, report.status.name)
                ps.setTimestamp(i++, Timestamp.from(report.startTime))
                ps.setString(i++, report.alert?.service)
                ps.setString(i++, report.alert?.severity)
                ps.setString(i++, report.verification?.expectedComponent)
                ps.setString(i++, report.verification?.expectedCauseType)
                ps.setString(i++, aiModel)
                aiTemp?.let { ps.setDouble(i++, it) } ?: ps.setNull(i++, java.sql.Types.DOUBLE)
                report.durationMs?.let { ps.setLong(i++, it) } ?: ps.setNull(i++, java.sql.Types.BIGINT)
                report.verification?.keywordCoverage?.let { ps.setDouble(i++, it) } ?: ps.setNull(i++, java.sql.Types.DOUBLE)
                report.verification?.requiredKeywordCoverage?.let { ps.setDouble(i++, it) } ?: ps.setNull(i++, java.sql.Types.DOUBLE)
                report.verification?.keywordsFound?.size?.let { ps.setInt(i++, it) } ?: ps.setNull(i++, java.sql.Types.INTEGER)
                report.verification?.keywordsMissing?.size?.let { ps.setInt(i++, it) } ?: ps.setNull(i++, java.sql.Types.INTEGER)
                report.priorKnowledge?.loadResult?.propositionsExtracted?.let { ps.setInt(i++, it) } ?: ps.setNull(i++, java.sql.Types.INTEGER)
                report.analysis?.analysisDurationMs?.let { ps.setLong(i++, it) } ?: ps.setNull(i++, java.sql.Types.BIGINT)
                report.performance?.priorKnowledgeLoadMs?.let { ps.setLong(i++, it) } ?: ps.setNull(i++, java.sql.Types.BIGINT)
                report.performance?.diceApiCalls?.let { ps.setInt(i++, it) } ?: ps.setNull(i++, java.sql.Types.INTEGER)
                report.performance?.datadogApiCalls?.let { ps.setInt(i++, it) } ?: ps.setNull(i++, java.sql.Types.INTEGER)
                ps.setBoolean(i++, report.passed)
                report.verification?.componentIdentified?.let { ps.setBoolean(i++, it) } ?: ps.setNull(i++, java.sql.Types.BOOLEAN)
                report.verification?.causeTypeIdentified?.let { ps.setBoolean(i++, it) } ?: ps.setNull(i++, java.sql.Types.BOOLEAN)
                ps.setString(i++, report.error)
                ps.setString(i++, report.verification?.actualRootCause)
                ps.setString(i++, expectedKeywordsJson)
                ps.setString(i++, aiParamsJson)
                ps.setString(i++, reportJson)
                ps.executeUpdate()
            }
        }
        logger.debug("Persisted report to H2: ${report.testName} (run=$runId)")
    }
}
