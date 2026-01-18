package com.example.rca.analysis

import com.example.rca.domain.*
import org.springframework.stereotype.Component

/**
 * Engine for scoring and ranking root cause candidates.
 */
@Component
class ScoringEngine {

    /**
     * Score log clusters and convert to candidates.
     */
    fun scoreLogClusters(clusters: List<LogCluster>, limit: Int = 10): List<Candidate> {
        return clusters
            .map { cluster ->
                val score = calculateLogClusterScore(cluster)
                candidate(CandidateKind.LOGS, "Log pattern: ${cluster.template.take(80)}") {
                    score(score)
                    evidence(mapOf(
                        "fingerprint" to cluster.fingerprint,
                        "template" to cluster.template,
                        "count_incident" to cluster.countIncident,
                        "count_baseline" to cluster.countBaseline,
                        "is_new_pattern" to cluster.isNewPattern,
                        "growth_ratio" to cluster.growthRatio,
                        "sample" to (cluster.sample ?: emptyMap<String, Any>())
                    ))
                }
            }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun calculateLogClusterScore(cluster: LogCluster): Double {
        // New patterns are highly suspicious
        if (cluster.isNewPattern) {
            return (0.7 + (cluster.countIncident.coerceAtMost(100) / 100.0) * 0.25)
                .coerceAtMost(0.95)
        }

        // Growth-based scoring
        val growthScore = when {
            cluster.growthRatio >= 10.0 -> 0.8
            cluster.growthRatio >= 5.0 -> 0.6
            cluster.growthRatio >= 2.0 -> 0.4
            cluster.growthRatio >= 1.5 -> 0.2
            else -> 0.1
        }

        // Volume bonus
        val volumeBonus = (cluster.countIncident.coerceAtMost(500) / 500.0) * 0.15

        return (growthScore + volumeBonus).coerceIn(0.0, 0.95)
    }

    /**
     * Merge and rank all candidates from different sources.
     */
    fun mergeAndRank(
        logCandidates: List<Candidate>,
        apmCandidates: List<Candidate>,
        eventCandidates: List<Candidate> = emptyList(),
        limit: Int = 20
    ): List<Candidate> {
        return (logCandidates + apmCandidates + eventCandidates)
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Generate recommendations based on symptoms and candidates.
     */
    fun generateRecommendations(
        symptoms: List<Symptom>,
        candidates: List<Candidate>,
        events: List<com.example.rca.datadog.dto.EventEntry>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // Check symptom severity
        symptoms.forEach { symptom ->
            val pct = symptom.percentChange ?: 0.0
            if (pct > 20) {
                recommendations.add(
                    "Confirm the regression start time using the incident window and the symptom peak timestamp."
                )
            }
        }

        // Log pattern recommendations
        val logCandidates = candidates.filter { it.kind == CandidateKind.LOGS }
        if (logCandidates.isNotEmpty()) {
            recommendations.add(
                "Inspect the top log signature(s) and trace correlation (if available) to identify the failing component."
            )
        }

        // Event-based recommendations
        if (events.isNotEmpty()) {
            val deployEvents = events.filter { it.isDeployment() }
            if (deployEvents.isNotEmpty()) {
                recommendations.add(
                    "Review deploy/config/autoscaling events near the incident start for temporal alignment."
                )
            }
        }

        // Dependency recommendations
        val depCandidates = candidates.filter { it.kind == CandidateKind.DEPENDENCY }
        if (depCandidates.isNotEmpty()) {
            recommendations.add(
                "Investigate downstream services showing degradation: ${
                    depCandidates.take(3).joinToString(", ") { 
                        it.evidence["dependency"]?.toString() ?: "unknown" 
                    }
                }"
            )
        }

        // Endpoint recommendations
        val endpointCandidates = candidates.filter { it.kind == CandidateKind.ENDPOINT }
        if (endpointCandidates.isNotEmpty()) {
            recommendations.add(
                "If APM is enabled, pivot to the slowest endpoints and downstream services during the incident window."
            )
        }

        // General fallback
        if (recommendations.isEmpty()) {
            recommendations.add(
                "Review the incident timeline and correlate with any recent changes or external dependencies."
            )
        }

        return recommendations.distinct()
    }
}
