package com.example.testreport.service

import com.example.testreport.repository.TestRunRepository
import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

data class ResetDbResult(val deleted: Int, val logsDeleted: Int, val analysisDeleted: Int)

data class PurgeBeforeResult(val deleted: Int, val logsDeleted: Int, val analysisDeleted: Int)

data class ClearLogsResult(val logsDeleted: Int, val analysisDeleted: Int)

@Service
class AdminService(
        private val repo: TestRunRepository,
        @Value("\${run-tests.project-root:..}") private val projectRoot: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun logFilesDir(): File = File(System.getProperty("java.io.tmpdir"))

    private fun reportsDir(): File {
        val root = File(projectRoot).absoluteFile
        val candidate = File(root, "embabel-dice-rca/test-reports")
        if (candidate.isDirectory) return candidate
        val cwd = File(System.getProperty("user.dir"))
        val fallback = File(cwd, "embabel-dice-rca/test-reports")
        return if (fallback.isDirectory) fallback else candidate
    }

    /** List log files created by test runs (test-run-*.log in temp dir). */
    private fun listLogFiles(): List<File> {
        val dir = logFilesDir()
        if (!dir.isDirectory) return emptyList()
        val files =
                dir.listFiles { f ->
                    f.isFile && f.name.startsWith("test-run-") && f.name.endsWith(".log")
                }
                        ?: return emptyList()
        return files.toList()
    }

    /** List analysis files: run-*-analysis.md and analysis-suggestions-*.md in test-reports. */
    private fun listAnalysisFiles(): List<File> {
        val dir = reportsDir()
        if (!dir.isDirectory) return emptyList()
        val files =
                dir.listFiles { f ->
                    if (!f.isFile || !f.name.endsWith(".md")) return@listFiles false
                    f.name.startsWith("run-") && f.name.endsWith("-analysis.md") ||
                            f.name.startsWith("analysis-suggestions-")
                }
                        ?: return emptyList()
        return files.toList()
    }

    /**
     * Reset DB: delete all test_runs. If [clearLogs], also delete all test-run-*.log and all
     * analysis files (run-*-analysis.md, analysis-suggestions-*.md) in test-reports.
     */
    fun resetDb(clearLogs: Boolean): ResetDbResult {
        val deleted = repo.deleteAll()
        var logsDeleted = 0
        var analysisDeleted = 0
        if (clearLogs) {
            logsDeleted = deleteLogFiles(null)
            analysisDeleted = deleteAnalysisFiles(null)
        }
        log.info(
                "Reset DB: deleted {} rows, {} log files, {} analysis files",
                deleted,
                logsDeleted,
                analysisDeleted
        )
        return ResetDbResult(deleted, logsDeleted, analysisDeleted)
    }

    /**
     * Purge DB rows with started_at < [before]. If [clearLogs], also delete log files and analysis
     * files with lastModified < [before].
     */
    fun purgeBefore(before: Instant, clearLogs: Boolean): PurgeBeforeResult {
        val deleted = repo.deleteBefore(before)
        var logsDeleted = 0
        var analysisDeleted = 0
        if (clearLogs) {
            logsDeleted = deleteLogFiles(before)
            analysisDeleted = deleteAnalysisFiles(before)
        }
        log.info(
                "Purge before {}: deleted {} rows, {} log files, {} analysis files",
                before,
                deleted,
                logsDeleted,
                analysisDeleted
        )
        return PurgeBeforeResult(deleted, logsDeleted, analysisDeleted)
    }

    /**
     * Delete test-run-*.log and analysis files. If [before] is set, only delete files with
     * lastModified < before; else delete all.
     */
    fun clearLogFiles(before: Instant?): ClearLogsResult {
        val logsDeleted = deleteLogFiles(before)
        val analysisDeleted = deleteAnalysisFiles(before)
        log.info(
                "Clear logs + analysis (before={}): deleted {} log files, {} analysis files",
                before,
                logsDeleted,
                analysisDeleted
        )
        return ClearLogsResult(logsDeleted, analysisDeleted)
    }

    private fun deleteLogFiles(before: Instant?): Int {
        val cutoff = before?.toEpochMilli() ?: Long.MAX_VALUE
        var deleted = 0
        for (f in listLogFiles()) {
            if (f.lastModified() < cutoff) {
                if (f.delete()) deleted++
                else log.warn("Could not delete log file: {}", f.absolutePath)
            }
        }
        return deleted
    }

    private fun deleteAnalysisFiles(before: Instant?): Int {
        val cutoff = before?.toEpochMilli() ?: Long.MAX_VALUE
        var deleted = 0
        for (f in listAnalysisFiles()) {
            if (f.lastModified() < cutoff) {
                if (f.delete()) deleted++
                else log.warn("Could not delete analysis file: {}", f.absolutePath)
            }
        }
        return deleted
    }

    fun parseInstant(s: String): Instant? =
            try {
                Instant.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
}
