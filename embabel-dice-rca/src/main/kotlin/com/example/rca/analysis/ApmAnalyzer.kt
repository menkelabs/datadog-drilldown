package com.example.rca.analysis

import com.example.rca.datadog.dto.SpanEntry
import com.example.rca.datadog.dto.SpanKind
import com.example.rca.datadog.dto.stats
import com.example.rca.domain.Candidate
import com.example.rca.domain.CandidateKind
import com.example.rca.domain.candidate
import org.springframework.stereotype.Component

/**
 * Analyzer for APM/trace data to identify endpoint and dependency issues.
 */
@Component
class ApmAnalyzer {

    /**
     * Analyze APM spans to find endpoint and dependency candidates.
     */
    fun analyzeSpans(
        incidentSpans: List<SpanEntry>,
        baselineSpans: List<SpanEntry>,
        mode: AnalysisMode = AnalysisMode.LATENCY
    ): ApmAnalysisResult {
        // Separate server and client spans
        val incServer = incidentSpans.filter { it.spanKind == SpanKind.SERVER }
        val baseServer = baselineSpans.filter { it.spanKind == SpanKind.SERVER }
        val incClient = incidentSpans.filter { it.spanKind == SpanKind.CLIENT }
        val baseClient = baselineSpans.filter { it.spanKind == SpanKind.CLIENT }

        // Analyze endpoints (server spans grouped by resource)
        val endpointCandidates = analyzeEndpoints(
            incServer.ifEmpty { incidentSpans },
            baseServer.ifEmpty { baselineSpans },
            mode
        )

        // Analyze dependencies (client spans grouped by peer service)
        val dependencyCandidates = analyzeDependencies(incClient, baseClient)

        val candidates = (endpointCandidates + dependencyCandidates)
            .sortedByDescending { it.score }

        return ApmAnalysisResult(
            incidentSpanCount = incidentSpans.size,
            baselineSpanCount = baselineSpans.size,
            serverSpanCount = incServer.size,
            clientSpanCount = incClient.size,
            endpointCandidates = endpointCandidates,
            dependencyCandidates = dependencyCandidates,
            topCandidates = candidates.take(10)
        )
    }

    private fun analyzeEndpoints(
        incidentSpans: List<SpanEntry>,
        baselineSpans: List<SpanEntry>,
        mode: AnalysisMode
    ): List<Candidate> {
        val incByResource = incidentSpans.groupBy { it.resource }
        val baseByResource = baselineSpans.groupBy { it.resource }

        val candidates = mutableListOf<Candidate>()

        for ((resource, incSpans) in incByResource) {
            val baseSpans = baseByResource[resource] ?: emptyList()
            val incStats = incSpans.stats()
            val baseStats = baseSpans.stats()

            val (delta, score) = when (mode) {
                AnalysisMode.ERRORS -> {
                    val errDelta = incStats.errorRate - baseStats.errorRate
                    errDelta to (errDelta / 0.5).coerceIn(0.0, 0.95)
                }
                AnalysisMode.LATENCY -> {
                    val p95Delta = incStats.p95Ms - baseStats.p95Ms
                    p95Delta to (p95Delta / 500.0).coerceIn(0.0, 0.95)
                }
                AnalysisMode.THROUGHPUT -> {
                    val countDelta = (baseStats.count - incStats.count).toDouble()
                    countDelta to (countDelta / 100.0).coerceIn(0.0, 0.95)
                }
            }

            if (score > 0.1) {
                candidates.add(candidate(CandidateKind.ENDPOINT, "Endpoint regression: $resource") {
                    score(score)
                    evidence(mapOf(
                        "resource" to resource,
                        "incident" to mapOf(
                            "count" to incStats.count,
                            "p95_ms" to incStats.p95Ms,
                            "error_rate" to incStats.errorRate
                        ),
                        "baseline" to mapOf(
                            "count" to baseStats.count,
                            "p95_ms" to baseStats.p95Ms,
                            "error_rate" to baseStats.errorRate
                        ),
                        "delta" to delta
                    ))
                })
            }
        }

        return candidates.sortedByDescending { it.score }
    }

    private fun analyzeDependencies(
        incidentSpans: List<SpanEntry>,
        baselineSpans: List<SpanEntry>
    ): List<Candidate> {
        val incByDep = incidentSpans.groupBy { it.dependencyName() ?: it.name ?: "unknown" }
        val baseByDep = baselineSpans.groupBy { it.dependencyName() ?: it.name ?: "unknown" }

        val candidates = mutableListOf<Candidate>()

        for ((dep, incSpans) in incByDep) {
            val baseSpans = baseByDep[dep] ?: emptyList()
            val incStats = incSpans.stats()
            val baseStats = baseSpans.stats()

            val durDelta = incStats.totalDurationMs - baseStats.totalDurationMs
            val errDelta = incStats.errorRate - baseStats.errorRate

            if (durDelta > 0 || errDelta > 0) {
                val score = ((durDelta / 2000.0) + (errDelta / 0.5)).coerceIn(0.0, 0.95)

                if (score > 0.1) {
                    candidates.add(candidate(CandidateKind.DEPENDENCY, "Downstream suspect: $dep") {
                        score(score)
                        evidence(mapOf(
                            "dependency" to dep,
                            "incident" to mapOf(
                                "count" to incStats.count,
                                "total_duration_ms" to incStats.totalDurationMs,
                                "error_rate" to incStats.errorRate
                            ),
                            "baseline" to mapOf(
                                "count" to baseStats.count,
                                "total_duration_ms" to baseStats.totalDurationMs,
                                "error_rate" to baseStats.errorRate
                            ),
                            "duration_delta_ms" to durDelta,
                            "error_rate_delta" to errDelta
                        ))
                    })
                }
            }
        }

        return candidates.sortedByDescending { it.score }
    }
}

/**
 * Result of APM analysis.
 */
data class ApmAnalysisResult(
    val incidentSpanCount: Int,
    val baselineSpanCount: Int,
    val serverSpanCount: Int,
    val clientSpanCount: Int,
    val endpointCandidates: List<Candidate>,
    val dependencyCandidates: List<Candidate>,
    val topCandidates: List<Candidate>
) {
    val enabled: Boolean = true

    fun toMap(): Map<String, Any> = mapOf(
        "enabled" to enabled,
        "counts" to mapOf(
            "incident_spans" to incidentSpanCount,
            "baseline_spans" to baselineSpanCount,
            "server_spans" to serverSpanCount,
            "client_spans" to clientSpanCount
        ),
        "endpoint_candidates" to endpointCandidates.size,
        "dependency_candidates" to dependencyCandidates.size
    )
}
