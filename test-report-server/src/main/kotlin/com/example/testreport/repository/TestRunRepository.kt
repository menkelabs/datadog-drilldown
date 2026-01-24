package com.example.testreport.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
class TestRunRepository(private val jdbc: JdbcTemplate) {

    fun runSummaries(limit: Int = 20): List<RunSummaryDto> {
        val sql = """
            SELECT run_id, MIN(started_at) AS run_start,
                   COUNT(*) AS total,
                   SUM(CASE WHEN passed THEN 1 ELSE 0 END) AS passed,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS errors
            FROM test_runs
            GROUP BY run_id
            ORDER BY run_start DESC
            LIMIT ?
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> mapRunSummary(rs) }, limit)
    }

    fun runsByRunId(runId: String): List<TestRunRowDto> {
        val sql = """
            SELECT id, run_id, test_name, test_class, scenario_id, status, started_at,
                   duration_ms, keyword_coverage, passed, error_message, actual_root_cause
            FROM test_runs WHERE run_id = ? ORDER BY started_at
        """.trimIndent()
        return jdbc.query(sql, { rs, _ -> mapRunRow(rs) }, runId)
    }

    fun runById(id: Long): TestRunDetailDto? {
        val sql = """
            SELECT id, run_id, test_name, test_class, scenario_id, status, started_at,
                   duration_ms, keyword_coverage, passed, error_message, actual_root_cause, report_json
            FROM test_runs WHERE id = ?
        """.trimIndent()
        val list = jdbc.query(sql, { rs, _ -> mapRunDetail(rs) }, id)
        return list.firstOrNull()
    }

    fun list(runId: String?, scenarioId: String?, status: String?, limit: Int): List<TestRunRowDto> {
        var sql = """
            SELECT id, run_id, test_name, test_class, scenario_id, status, started_at,
                   duration_ms, keyword_coverage, passed, error_message, actual_root_cause
            FROM test_runs WHERE 1=1
        """.trimIndent()
        val args = mutableListOf<Any>()
        if (!runId.isNullOrBlank()) { sql += " AND run_id = ?"; args.add(runId) }
        if (!scenarioId.isNullOrBlank()) { sql += " AND scenario_id = ?"; args.add(scenarioId) }
        if (!status.isNullOrBlank()) { sql += " AND status = ?"; args.add(status) }
        sql += " ORDER BY started_at DESC LIMIT ?"
        args.add(limit)
        return jdbc.query(sql, { rs, _ -> mapRunRow(rs) }, *args.toTypedArray())
    }

    fun analysisSummary(): AnalysisSummaryDto? {
        val sql = """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN passed THEN 1 ELSE 0 END) AS passed,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                   SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS errors
            FROM test_runs
        """.trimIndent()
        val list = jdbc.query(sql) { rs, _ ->
            AnalysisSummaryDto(
                total = rs.getLong(1),
                passed = rs.getLong(2),
                failed = rs.getLong(3),
                errors = rs.getLong(4)
            )
        }
        return list.firstOrNull()
    }

    /** Delete all rows. Returns number deleted. */
    fun deleteAll(): Int =
        jdbc.update("DELETE FROM test_runs")

    /** Delete rows with started_at < [before]. Returns number deleted. */
    fun deleteBefore(before: Instant): Int =
        jdbc.update("DELETE FROM test_runs WHERE started_at < ?", java.sql.Timestamp.from(before))

    private fun mapRunSummary(rs: ResultSet) = RunSummaryDto(
        runId = rs.getString("run_id"),
        runStart = rs.getTimestamp("run_start")!!.toInstant(),
        total = rs.getInt("total"),
        passed = rs.getInt("passed"),
        failed = rs.getInt("failed"),
        errors = rs.getInt("errors")
    )

    private fun mapRunRow(rs: ResultSet) = TestRunRowDto(
        id = rs.getLong("id"),
        runId = rs.getString("run_id"),
        testName = rs.getString("test_name"),
        testClass = rs.getString("test_class"),
        scenarioId = rs.getString("scenario_id"),
        status = rs.getString("status"),
        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
        durationMs = rs.getObject("duration_ms")?.let { (it as Number).toLong() },
        keywordCoverage = rs.getObject("keyword_coverage")?.let { (it as Number).toDouble() },
        passed = rs.getBoolean("passed"),
        errorMessage = rs.getString("error_message"),
        actualRootCause = rs.getString("actual_root_cause")
    )

    private fun mapRunDetail(rs: ResultSet) = TestRunDetailDto(
        id = rs.getLong("id"),
        runId = rs.getString("run_id"),
        testName = rs.getString("test_name"),
        testClass = rs.getString("test_class"),
        scenarioId = rs.getString("scenario_id"),
        status = rs.getString("status"),
        startedAt = rs.getTimestamp("started_at")!!.toInstant(),
        durationMs = rs.getObject("duration_ms")?.let { (it as Number).toLong() },
        keywordCoverage = rs.getObject("keyword_coverage")?.let { (it as Number).toDouble() },
        passed = rs.getBoolean("passed"),
        errorMessage = rs.getString("error_message"),
        actualRootCause = rs.getString("actual_root_cause"),
        reportJson = rs.getString("report_json")
    )
}

data class RunSummaryDto(
    val runId: String,
    val runStart: java.time.Instant,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val errors: Int
)

data class TestRunRowDto(
    val id: Long,
    val runId: String,
    val testName: String,
    val testClass: String,
    val scenarioId: String?,
    val status: String,
    val startedAt: java.time.Instant,
    val durationMs: Long?,
    val keywordCoverage: Double?,
    val passed: Boolean,
    val errorMessage: String?,
    val actualRootCause: String?
)

data class TestRunDetailDto(
    val id: Long,
    val runId: String,
    val testName: String,
    val testClass: String,
    val scenarioId: String?,
    val status: String,
    val startedAt: java.time.Instant,
    val durationMs: Long?,
    val keywordCoverage: Double?,
    val passed: Boolean,
    val errorMessage: String?,
    val actualRootCause: String?,
    val reportJson: String?
)

data class AnalysisSummaryDto(val total: Long, val passed: Long, val failed: Long, val errors: Long)
