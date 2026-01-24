package com.example.testreport.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

data class SuggestionFile(val filename: String, val content: String, val modified: Long)

@Service
class AnalysisSuggestionsService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${run-tests.project-root:..}")
    private lateinit var projectRoot: String

    /** Directory containing run-*-analysis.md and legacy analysis-suggestions-*.md (test-reports). */
    private fun reportsDir(): File {
        val root = File(projectRoot).absoluteFile
        val candidate = File(root, "embabel-dice-rca/test-reports")
        if (candidate.isDirectory) return candidate
        // Fallback: CWD may be repo root (e.g. mvn -f test-report-server/pom.xml spring-boot:run)
        val cwd = File(System.getProperty("user.dir"))
        val fallback = File(cwd, "embabel-dice-rca/test-reports")
        return if (fallback.isDirectory) fallback else candidate
    }

    /**
     * Find all analysis files: run-*-analysis.md (new, runId key) and
     * analysis-suggestions-*.md (legacy). Newest first.
     * Returns latest content and list of all (filename, content, modified).
     */
    fun getSuggestions(): Map<String, Any?> {
        val dir = reportsDir()
        if (!dir.isDirectory) {
            log.debug("Reports dir not found: {}", dir.absolutePath)
            return mapOf("latest" to null, "all" to emptyList<SuggestionFile>())
        }
        val files = dir.listFiles { f ->
            if (!f.isFile || !f.name.endsWith(".md")) return@listFiles false
            f.name.startsWith("run-") && f.name.endsWith("-analysis.md") ||
                f.name.startsWith("analysis-suggestions-")
        } ?: emptyArray()
        val list = files
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val content = try {
                    f.readText()
                } catch (e: Exception) {
                    log.warn("Failed to read {}: {}", f.name, e.message)
                    ""
                }
                SuggestionFile(f.name, content, f.lastModified())
            }
        val latest = list.firstOrNull()
        return mapOf(
            "latest" to latest,
            "all" to list,
        )
    }
}
