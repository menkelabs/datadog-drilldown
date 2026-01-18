package com.example.rca.analysis

import com.example.rca.datadog.dto.LogEntry
import com.example.rca.domain.LogCluster
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Analyzer for clustering and ranking log entries.
 */
@Component
class LogAnalyzer {

    /**
     * Cluster logs by fingerprint (message pattern + error type).
     */
    fun clusterLogs(logs: List<LogEntry>): List<LogCluster> {
        if (logs.isEmpty()) return emptyList()

        val groups = logs.groupBy { it.fingerprint() }

        return groups.map { (fingerprint, entries) ->
            val firstEntry = entries.minByOrNull { it.timestamp } ?: entries.first()
            val template = generateTemplate(entries)
            val sample = entries.firstOrNull()?.let { buildSampleMap(it) }

            LogCluster(
                fingerprint = fingerprint,
                template = template,
                countIncident = entries.size,
                countBaseline = 0,
                firstSeen = firstEntry.timestamp,
                sample = sample
            )
        }
    }

    /**
     * Merge baseline counts into incident clusters.
     */
    fun mergeBaselineCounts(
        incidentClusters: List<LogCluster>,
        baselineLogs: List<LogEntry>
    ): List<LogCluster> {
        val baselineCounts = baselineLogs
            .groupBy { it.fingerprint() }
            .mapValues { it.value.size }

        return incidentClusters.map { cluster ->
            cluster.copy(
                countBaseline = baselineCounts[cluster.fingerprint] ?: 0
            )
        }
    }

    /**
     * Rank clusters by anomaly score (new patterns and growth).
     */
    fun rankClusters(clusters: List<LogCluster>, limit: Int = 10): List<LogCluster> {
        return clusters
            .sortedByDescending { it.anomalyScore() }
            .take(limit)
    }

    /**
     * Generate a template from multiple log messages by finding common patterns.
     */
    private fun generateTemplate(entries: List<LogEntry>): String {
        if (entries.isEmpty()) return ""
        if (entries.size == 1) return LogEntry.normalizeMessage(entries.first().message)

        // Use the normalized message from the first entry as the template
        val messages = entries.map { LogEntry.normalizeMessage(it.message) }
        return findCommonPrefix(messages) ?: messages.first()
    }

    private fun findCommonPrefix(strings: List<String>): String? {
        if (strings.isEmpty()) return null
        if (strings.size == 1) return strings.first()

        val first = strings.first()
        var prefixLen = first.length

        for (s in strings.drop(1)) {
            while (prefixLen > 0 && !s.startsWith(first.substring(0, prefixLen))) {
                prefixLen--
            }
        }

        return if (prefixLen > 20) first.substring(0, prefixLen) else strings.first()
    }

    private fun buildSampleMap(entry: LogEntry): Map<String, Any> {
        return buildMap {
            put("message", entry.message)
            entry.service?.let { put("service", it) }
            entry.host?.let { put("host", it) }
            entry.errorType()?.let { put("error.type", it) }
            entry.errorMessage()?.let { put("error.message", it) }
            entry.errorStack()?.let { put("error.stack", it) }
            entry.httpStatusCode()?.let { put("http.status_code", it) }
        }
    }
}

/**
 * Data class for log analysis results.
 */
data class LogAnalysisResult(
    val clusters: List<LogCluster>,
    val totalIncidentLogs: Int,
    val totalBaselineLogs: Int,
    val newPatterns: List<LogCluster>,
    val growthPatterns: List<LogCluster>
) {
    val volumeChange: Double
        get() = if (totalBaselineLogs > 0) {
            ((totalIncidentLogs - totalBaselineLogs).toDouble() / totalBaselineLogs) * 100
        } else {
            if (totalIncidentLogs > 0) Double.POSITIVE_INFINITY else 0.0
        }
}
