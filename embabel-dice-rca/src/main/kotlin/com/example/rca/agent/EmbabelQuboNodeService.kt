package com.example.rca.agent

import com.example.rca.dice.qubo.QuboIntegrationProperties
import com.example.rca.dice.qubo.QuboReportEnricher
import com.example.rca.domain.Candidate
import com.example.rca.domain.CandidateKind
import com.example.rca.domain.IncidentContext
import com.example.rca.domain.Report
import com.example.rca.domain.ReportMeta
import com.example.rca.domain.Scope
import com.example.rca.domain.SeedType
import com.example.rca.domain.Windows
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Bridges Embabel [RootCauseAnalysis] candidates into the same QUBO path as [com.example.rca.agent.RcaAgent]
 * ([QuboReportEnricher]). Used by [IncidentInvestigatorAgent.runQuboShortlist].
 */
@Service
class EmbabelQuboNodeService(
    private val quboProperties: QuboIntegrationProperties,
    private val quboReportEnricher: QuboReportEnricher,
    private val rcaProperties: RcaAgentProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun runShortlist(request: IncidentRequest, analysis: RootCauseAnalysis): QuboShortlistResult {
        if (!quboProperties.enabled) {
            return QuboShortlistResult(
                skipped = true,
                reason = "qubo_disabled",
                findings = emptyMap(),
                recommendations = emptyList(),
            )
        }
        if (analysis.candidates.isEmpty()) {
            return QuboShortlistResult(
                skipped = true,
                reason = "no_candidates",
                findings = emptyMap(),
                recommendations = emptyList(),
            )
        }

        val candidates = analysis.candidates.map { it.toDomainCandidate() }
        val anchor = Instant.now()
        val windows = Windows.endingAt(
            anchor,
            rcaProperties.defaultWindowMinutes,
            rcaProperties.defaultBaselineMinutes,
        )
        val scope = Scope(service = request.service, env = request.env)
        val incidentId = "EMB-${request.service ?: "svc"}-${UUID.randomUUID().toString().substring(0, 8)}"
        val ctx = IncidentContext(incidentId, windows, scope)

        val report = Report(
            meta = ReportMeta(
                seedType = SeedType.MANUAL,
                generatedAt = anchor,
                ddSite = "datadoghq.com",
                input = mapOf(
                    "source" to "embabel_incident_investigator",
                    "service" to (request.service ?: ""),
                    "env" to (request.env ?: ""),
                ),
            ),
            windows = windows,
            scope = scope,
            symptoms = emptyList(),
            findings = emptyMap(),
            recommendations = emptyList(),
            candidates = candidates,
        )

        log.debug("Embabel QUBO node: {} candidates for incident {}", candidates.size, incidentId)
        val enriched = quboReportEnricher.enrich(report, ctx)
        @Suppress("UNCHECKED_CAST")
        val normalized = enriched.findings["qubo"] as? Map<String, Any> ?: emptyMap()

        val extra = if (enriched.recommendations.size > report.recommendations.size) {
            enriched.recommendations.drop(report.recommendations.size)
        } else {
            emptyList()
        }

        return QuboShortlistResult(
            skipped = false,
            reason = null,
            findings = normalized,
            recommendations = extra,
        )
    }
}

/** Result of the named Embabel DAG step `runQuboShortlist`; fed into `generateReport`. */
data class QuboShortlistResult(
    val skipped: Boolean,
    val reason: String? = null,
    val findings: Map<String, Any> = emptyMap(),
    val recommendations: List<String> = emptyList(),
) {
    fun promptSection(): String = when {
        skipped -> "QUBO solver: skipped (${reason ?: "n/a"})."
        else -> buildString {
            appendLine("QUBO solver (dice-leap-poc) output:")
            findings["selected_candidate_titles"]?.let { appendLine("- Shortlist: $it") }
            findings["solve_record"]?.let { rec ->
                if (rec is Map<*, *>) {
                    appendLine("- solver_mode=${rec["solver_mode"]}, vs_baseline_delta=${rec["vs_baseline_delta"]}, runtime_ms=${rec["runtime_ms"]}")
                }
            }
            findings["error"]?.let { appendLine("- error: $it") }
            findings["fallback"]?.let { appendLine("- fallback: $it") }
            if (recommendations.isNotEmpty()) {
                appendLine("- Notes: ${recommendations.joinToString("; ")}")
            }
        }
    }
}

private fun RootCauseCandidate.toDomainCandidate(): Candidate {
    val kind = when (category.uppercase()) {
        "DEPENDENCY", "EXTERNAL" -> CandidateKind.DEPENDENCY
        "INFRASTRUCTURE" -> CandidateKind.INFRASTRUCTURE
        "DEPLOYMENT" -> CandidateKind.CHANGE
        "RESOURCE" -> CandidateKind.RESOURCE
        "CODE" -> CandidateKind.ERROR_PATTERN
        "ENDPOINT", "LATENCY" -> CandidateKind.ENDPOINT
        else -> CandidateKind.LOGS
    }
    return Candidate(kind, title, score.coerceIn(0.0, 1.0), mapOf("evidence" to evidence))
}
