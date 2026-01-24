package com.example.testreport.api

import com.example.testreport.service.AdminService
import com.example.testreport.service.ClearLogsResult
import com.example.testreport.service.PurgeBeforeResult
import com.example.testreport.service.ResetDbResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminApiController(private val admin: AdminService) {

    /**
     * Reset DB: delete all test_runs. Body: { "clearLogs": boolean }. If clearLogs, also delete all
     * test-run-*.log and analysis files in test-reports.
     */
    @PostMapping("/reset-db")
    fun resetDb(@RequestBody body: Map<String, Any>?): ResponseEntity<ResetDbResult> {
        val clearLogs = body?.get("clearLogs") == true
        val result = admin.resetDb(clearLogs)
        return ResponseEntity.ok(result)
    }

    /**
     * Purge DB rows with started_at < before. Body: { "before": "ISO8601", "clearLogs": boolean }.
     * If clearLogs, also delete log and analysis files with lastModified < before.
     */
    @PostMapping("/purge-before")
    fun purgeBefore(@RequestBody body: Map<String, Any>?): ResponseEntity<Any> {
        val beforeStr =
                body?.get("before") as? String
                        ?: return ResponseEntity.badRequest()
                                .body(
                                        mapOf(
                                                "error" to
                                                        "Missing 'before' (ISO8601 date, e.g. 2026-01-24T00:00:00Z)"
                                        )
                                )
        val before =
                admin.parseInstant(beforeStr)
                        ?: return ResponseEntity.badRequest()
                                .body(mapOf("error" to "Invalid 'before' date"))
        val clearLogs = body["clearLogs"] == true
        val result: PurgeBeforeResult = admin.purgeBefore(before, clearLogs)
        return ResponseEntity.ok(result)
    }

    /**
     * Clear log files (test-run-*.log) and analysis files (run-*-analysis.md,
     * analysis-suggestions-*.md). Body: { "before": "ISO8601" | null }. If before set, only delete
     * files with lastModified < before; else delete all.
     */
    @PostMapping("/clear-logs")
    fun clearLogs(@RequestBody body: Map<String, Any>?): ResponseEntity<Any> {
        val beforeStr = body?.get("before") as? String
        val before = if (beforeStr != null) admin.parseInstant(beforeStr) else null
        if (beforeStr != null && before == null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Invalid 'before' date"))
        }
        val result: ClearLogsResult = admin.clearLogFiles(before)
        return ResponseEntity.ok(result)
    }
}
