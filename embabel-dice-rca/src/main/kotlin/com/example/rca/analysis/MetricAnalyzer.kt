package com.example.rca.analysis

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.dto.MetricResponse
import com.example.rca.datadog.dto.percentChange
import com.example.rca.datadog.dto.summarize
import com.example.rca.domain.*
import org.springframework.stereotype.Component

/**
 * Analyzer for metric data to detect symptoms.
 */
@Component
class MetricAnalyzer {

    /**
     * Analyze metrics for a monitor query.
     */
    fun analyzeMonitorMetric(
        query: String,
        baselineResponse: MetricResponse,
        incidentResponse: MetricResponse
    ): Symptom {
        val baseSummary = baselineResponse.summarize()
        val incSummary = incidentResponse.summarize()

        return Symptom(
            type = inferSymptomType(query),
            queryOrSignature = query,
            baselineValue = baseSummary.value,
            incidentValue = incSummary.value,
            percentChange = percentChange(baseSummary.value, incSummary.value),
            peakTs = incSummary.peakTs?.let { java.time.Instant.ofEpochSecond(it) },
            peakValue = incSummary.peakValue
        )
    }

    /**
     * Analyze log volume as a symptom.
     */
    fun analyzeLogVolume(
        query: String,
        baselineCount: Int,
        incidentCount: Int
    ): Symptom {
        return Symptom(
            type = SymptomType.LOG_SIGNATURE,
            queryOrSignature = query,
            baselineValue = baselineCount.toDouble(),
            incidentValue = incidentCount.toDouble(),
            percentChange = percentChange(baselineCount.toDouble(), incidentCount.toDouble())
        )
    }

    /**
     * Create a service latency symptom.
     */
    fun createServiceSymptom(
        client: DatadogClient,
        scope: Scope,
        windows: Windows,
        mode: AnalysisMode
    ): Symptom {
        val service = scope.service ?: return emptySymptom("No service specified")
        val env = scope.env

        val tagset = buildList {
            add("service:$service")
            env?.let { add("env:$it") }
        }.joinToString(",")
        val tagExpr = "{$tagset}"

        val queries = when (mode) {
            AnalysisMode.ERRORS -> listOf(
                "sum:trace.$service.request.errors$tagExpr.as_count()" to SymptomType.ERROR_RATE,
                "sum:trace.http.request.errors$tagExpr.as_count()" to SymptomType.ERROR_RATE
            )
            AnalysisMode.LATENCY -> listOf(
                "p95:trace.$service.request.duration$tagExpr" to SymptomType.LATENCY,
                "p95:trace.http.request.duration$tagExpr" to SymptomType.LATENCY
            )
            AnalysisMode.THROUGHPUT -> listOf(
                "sum:trace.$service.request.hits$tagExpr.as_count()" to SymptomType.THROUGHPUT
            )
        }

        for ((query, symptomType) in queries) {
            try {
                val baseline = client.queryMetrics(query, windows.baseline.start, windows.baseline.end)
                val incident = client.queryMetrics(query, windows.incident.start, windows.incident.end)

                val baseSummary = baseline.summarize()
                val incSummary = incident.summarize()

                if (baseSummary.pointCount > 0 || incSummary.pointCount > 0) {
                    return Symptom(
                        type = symptomType,
                        queryOrSignature = query,
                        baselineValue = baseSummary.value,
                        incidentValue = incSummary.value,
                        percentChange = percentChange(baseSummary.value, incSummary.value),
                        peakTs = incSummary.peakTs?.let { java.time.Instant.ofEpochSecond(it) },
                        peakValue = incSummary.peakValue
                    )
                }
            } catch (e: Exception) {
                // Try next query pattern
                continue
            }
        }

        return emptySymptom("No matching service metric found")
    }

    private fun emptySymptom(reason: String) = Symptom(
        type = SymptomType.METRIC,
        queryOrSignature = "($reason)"
    )

    /**
     * Infer symptom type from query string.
     */
    private fun inferSymptomType(query: String): SymptomType {
        val q = query.lowercase()
        return when {
            q.contains("p95") || q.contains("p99") || 
                q.contains("latency") || q.contains("duration") -> SymptomType.LATENCY
            q.contains("error") || q.contains("5xx") || 
                q.contains("exception") -> SymptomType.ERROR_RATE
            q.contains("memory") || q.contains("heap") -> SymptomType.MEMORY
            q.contains("cpu") -> SymptomType.CPU
            else -> SymptomType.METRIC
        }
    }
}

enum class AnalysisMode {
    LATENCY,
    ERRORS,
    THROUGHPUT
}
