/*
 * Embabel RCA Agent for Datadog Incident Investigation
 *
 * This agent uses the Embabel framework to provide AI-powered
 * root cause analysis for production incidents.
 */
package com.example.rca.agent

import com.embabel.agent.api.annotation.*
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.create
import com.embabel.agent.domain.io.UserInput
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.model.ModelProvider.Companion.CHEAPEST_ROLE
import com.example.rca.datadog.DatadogClient
import com.example.rca.domain.*
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

/**
 * Domain object representing an incident investigation request.
 */
data class IncidentRequest(
    val service: String?,
    val env: String?,
    val description: String,
    val startTime: Instant?,
    val endTime: Instant?,
    val monitorId: Long? = null,
    val logQuery: String? = null,
)

/**
 * Categorization of the incident type.
 */
enum class IncidentCategory {
    LATENCY,
    ERROR_RATE,
    AVAILABILITY,
    RESOURCE_EXHAUSTION,
    UNKNOWN
}

/**
 * Result of incident categorization.
 */
data class IncidentCategorization(
    val category: IncidentCategory,
    val reasoning: String,
)

/**
 * Evidence collected from Datadog.
 */
data class DatadogEvidence(
    val logClusters: List<LogCluster>,
    val symptoms: List<Symptom>,
    val events: List<String>,
    val apmFindings: Map<String, Any>,
)

/**
 * Analysis of the root cause candidates.
 */
data class RootCauseAnalysis(
    val candidates: List<Candidate>,
    val primarySuspect: String,
    val confidence: Double,
    val reasoning: String,
)

/**
 * Final investigation report with recommendations.
 */
data class InvestigationReport(
    val summary: String,
    val rootCause: RootCauseAnalysis,
    val recommendations: List<String>,
    val nextSteps: List<String>,
    val severity: Severity,
)

/**
 * Critique of the investigation analysis.
 */
data class AnalysisCritique(
    val accepted: Boolean,
    val reasoning: String,
    val improvementSuggestions: List<String> = emptyList(),
)

/**
 * Configuration properties for the RCA Agent.
 */
@ConfigurationProperties(prefix = "embabel.rca")
class RcaAgentProperties(
    val analysisModel: String = "gpt-4o",
    val fastModel: String = "gpt-4o-mini",
    val maxCandidates: Int = 10,
    val defaultWindowMinutes: Int = 30,
    val defaultBaselineMinutes: Int = 30,
)

/**
 * Embabel Agent for Root Cause Analysis of production incidents.
 *
 * This agent implements the Embabel model for autonomous incident investigation:
 * 1. Categorizes the incident type from user description
 * 2. Collects evidence from Datadog (logs, metrics, APM, events)
 * 3. Analyzes patterns and identifies root cause candidates
 * 4. Self-critiques the analysis for quality
 * 5. Generates actionable recommendations
 *
 * The agent uses multi-model approach:
 * - Fast model for categorization and initial triage
 * - Advanced model for deep analysis and reasoning
 */
@Agent(
    description = "Investigate production incidents and identify root causes using Datadog telemetry data",
)
class IncidentInvestigatorAgent(
    val properties: RcaAgentProperties,
    val datadogClient: DatadogClient,
) {
    private val logger = LoggerFactory.getLogger(IncidentInvestigatorAgent::class.java)

    init {
        logger.info("IncidentInvestigatorAgent initialized with properties: $properties")
    }

    /**
     * Parse and categorize the incident from user input.
     * Uses a fast model for efficient classification.
     */
    @Action
    fun categorizeIncident(
        userInput: UserInput,
        context: OperationContext,
    ): IncidentCategorization = context.ai()
        .withLlmByRole(CHEAPEST_ROLE)
        .create(
            """
            Analyze the following incident description and categorize it.

            Categories:
            - LATENCY: Performance degradation, slow responses, high p95/p99
            - ERROR_RATE: Increased errors, 5xx responses, exceptions
            - AVAILABILITY: Service down, connection failures, timeouts
            - RESOURCE_EXHAUSTION: Memory pressure, CPU saturation, connection pool depletion
            - UNKNOWN: Cannot determine from description

            Incident description:
            <${userInput.content}>

            Provide the category and your reasoning.
        """.trimIndent()
        )

    /**
     * Parse the incident request from user input and categorization.
     */
    @Action
    fun parseIncidentRequest(
        userInput: UserInput,
        categorization: IncidentCategorization,
        context: OperationContext,
    ): IncidentRequest = context.ai()
        .withLlmByRole(CHEAPEST_ROLE)
        .create(
            """
            Extract incident details from the user input.

            User input: <${userInput.content}>
            Category: ${categorization.category}

            Extract:
            - service name (if mentioned)
            - environment (prod, staging, etc.)
            - approximate start time (if mentioned, use ISO format)
            - any specific log query mentioned
            - monitor ID if referenced

            If not mentioned, leave as null.
        """.trimIndent()
        )

    /**
     * Collect evidence from Datadog based on the incident request.
     * This is a code-based action that calls the Datadog API.
     */
    @Action(post = [EVIDENCE_COLLECTED])
    fun collectDatadogEvidence(
        request: IncidentRequest,
        categorization: IncidentCategorization,
    ): DatadogEvidence {
        logger.info("Collecting Datadog evidence for ${request.service}/${request.env}")

        val now = Instant.now()
        val endTime = request.endTime ?: now
        val startTime = request.startTime
            ?: endTime.minusSeconds(properties.defaultWindowMinutes.toLong() * 60)

        val windows = Windows.fromRange(startTime, endTime)
        val scope = Scope(service = request.service, env = request.env)

        // Collect logs
        val logQuery = request.logQuery ?: buildDefaultLogQuery(scope, categorization)
        val incidentLogs = try {
            datadogClient.searchLogs(logQuery, windows.incident.start, windows.incident.end)
        } catch (e: Exception) {
            logger.warn("Failed to collect logs: ${e.message}")
            emptyList()
        }

        val baselineLogs = try {
            datadogClient.searchLogs(logQuery, windows.baseline.start, windows.baseline.end)
        } catch (e: Exception) {
            emptyList()
        }

        // Cluster logs
        val logAnalyzer = com.example.rca.analysis.LogAnalyzer()
        val clusters = logAnalyzer.clusterLogs(incidentLogs)
        val mergedClusters = logAnalyzer.mergeBaselineCounts(clusters, baselineLogs)
        val rankedClusters = logAnalyzer.rankClusters(mergedClusters, limit = 15)

        // Create symptom from log volume
        val symptoms = mutableListOf<Symptom>()
        val volumeChange = if (baselineLogs.isNotEmpty()) {
            ((incidentLogs.size - baselineLogs.size).toDouble() / baselineLogs.size) * 100
        } else if (incidentLogs.isNotEmpty()) {
            100.0
        } else {
            0.0
        }

        symptoms.add(Symptom(
            type = SymptomType.LOG_SIGNATURE,
            queryOrSignature = logQuery,
            baselineValue = baselineLogs.size.toDouble(),
            incidentValue = incidentLogs.size.toDouble(),
            percentChange = volumeChange
        ))

        // Collect events
        val events = try {
            val eventResponse = datadogClient.searchEvents(
                windows.incident.start,
                windows.incident.end,
                scope.toEventTagQuery()
            )
            eventResponse.events.take(10).map { "${it.title}: ${it.text.take(100)}" }
        } catch (e: Exception) {
            emptyList()
        }

        // Collect APM data if available
        val apmFindings = if (scope.isApmReady()) {
            collectApmFindings(scope, windows)
        } else {
            mapOf("enabled" to false, "reason" to "missing service/env")
        }

        return DatadogEvidence(
            logClusters = rankedClusters,
            symptoms = symptoms,
            events = events,
            apmFindings = apmFindings
        )
    }

    private fun buildDefaultLogQuery(scope: Scope, categorization: IncidentCategorization): String {
        val parts = mutableListOf<String>()
        scope.service?.let { parts.add("service:$it") }
        scope.env?.let { parts.add("env:$it") }

        when (categorization.category) {
            IncidentCategory.ERROR_RATE -> {
                parts.add("(@status:error OR status:error OR level:error OR @http.status_code:[500 TO 599])")
            }
            IncidentCategory.LATENCY -> {
                parts.add("(@status:error OR status:warn OR @duration:>1000)")
            }
            else -> {
                parts.add("(@status:error OR status:error OR level:error)")
            }
        }

        return parts.joinToString(" ")
    }

    private fun collectApmFindings(scope: Scope, windows: Windows): Map<String, Any> {
        val query = "service:${scope.service} env:${scope.env}"

        return try {
            val incidentSpans = datadogClient.searchSpans(
                query,
                windows.incident.start,
                windows.incident.end
            )
            val baselineSpans = datadogClient.searchSpans(
                query,
                windows.baseline.start,
                windows.baseline.end
            )

            mapOf<String, Any>(
                "enabled" to true,
                "incident_span_count" to incidentSpans.size,
                "baseline_span_count" to baselineSpans.size
            )
        } catch (e: Exception) {
            mapOf<String, Any>("enabled" to true, "error" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * Analyze collected evidence to identify root cause candidates.
     * Uses the advanced model for deep reasoning.
     */
    @Action(
        pre = [EVIDENCE_COLLECTED],
        post = [ANALYSIS_COMPLETE],
        canRerun = true,
    )
    fun analyzeRootCause(
        request: IncidentRequest,
        categorization: IncidentCategorization,
        evidence: DatadogEvidence,
        context: OperationContext,
    ): RootCauseAnalysis = context.ai()
        .withLlm(properties.analysisModel)
        .create(
            """
            Analyze the following evidence to identify root cause candidates for the incident.

            ## Incident Context
            - Service: ${request.service ?: "unknown"}
            - Environment: ${request.env ?: "unknown"}
            - Category: ${categorization.category}
            - Description: ${request.description}

            ## Evidence

            ### Log Patterns (${evidence.logClusters.size} clusters found)
            ${evidence.logClusters.take(10).joinToString("\n") { cluster ->
                """
                - Pattern: ${cluster.template.take(100)}
                  Count: ${cluster.countIncident} (baseline: ${cluster.countBaseline})
                  Growth: ${if (cluster.isNewPattern) "NEW PATTERN" else "${cluster.growthRatio}x"}
                """.trimIndent()
            }}

            ### Symptoms
            ${evidence.symptoms.joinToString("\n") { symptom ->
                "- ${symptom.type}: ${symptom.percentChange?.let { "%.1f%% change".format(it) } ?: "N/A"}"
            }}

            ### Recent Events
            ${evidence.events.joinToString("\n") { "- $it" }}

            ### APM Findings
            ${evidence.apmFindings}

            ## Task
            Based on this evidence:
            1. Identify the most likely root cause candidates (up to ${properties.maxCandidates})
            2. Rank them by likelihood (score 0.0 to 1.0)
            3. Identify the primary suspect
            4. Explain your reasoning

            Focus on:
            - New error patterns that appeared during the incident
            - Correlation with recent deployments or changes
            - Dependency failures indicated by error messages
            - Resource exhaustion patterns
        """.trimIndent()
        )

    /**
     * Critique the root cause analysis for quality and completeness.
     */
    @Action(
        pre = [ANALYSIS_COMPLETE],
        post = [ANALYSIS_SATISFACTORY],
        canRerun = true,
    )
    fun critiqueAnalysis(
        request: IncidentRequest,
        evidence: DatadogEvidence,
        analysis: RootCauseAnalysis,
        context: OperationContext,
    ): AnalysisCritique = context.ai()
        .withLlm(properties.analysisModel)
        .create(
            """
            Review this root cause analysis for quality and completeness.

            ## Incident
            ${request.description}

            ## Analysis to Review
            Primary Suspect: ${analysis.primarySuspect}
            Confidence: ${analysis.confidence}
            Reasoning: ${analysis.reasoning}

            Candidates identified: ${analysis.candidates.size}
            ${analysis.candidates.take(5).joinToString("\n") { "- ${it.title} (score: ${it.score})" }}

            ## Evidence Available
            - Log clusters: ${evidence.logClusters.size}
            - Symptoms: ${evidence.symptoms.size}
            - Events: ${evidence.events.size}

            ## Evaluation Criteria
            1. Does the analysis address the reported symptoms?
            2. Is the primary suspect well-supported by evidence?
            3. Are alternative hypotheses considered?
            4. Is the confidence level appropriate?
            5. Would the recommendations lead to resolution?

            Accept the analysis if it provides actionable insights.
            Reject if it's too vague or unsupported by evidence.
        """.trimIndent()
        )

    /**
     * Generate the final investigation report with recommendations.
     */
    @Action(
        pre = [ANALYSIS_SATISFACTORY],
        outputBinding = "finalReport",
    )
    @AchievesGoal(
        description = "Complete incident investigation with root cause analysis and recommendations",
    )
    fun generateReport(
        request: IncidentRequest,
        categorization: IncidentCategorization,
        evidence: DatadogEvidence,
        analysis: RootCauseAnalysis,
        critique: AnalysisCritique,
        context: OperationContext,
    ): InvestigationReport = context.ai()
        .withLlm(properties.analysisModel)
        .create(
            """
            Generate a comprehensive incident investigation report.

            ## Incident
            - Service: ${request.service ?: "unknown"}
            - Environment: ${request.env ?: "unknown"}
            - Category: ${categorization.category}
            - Description: ${request.description}

            ## Root Cause Analysis
            ${analysis.reasoning}

            Primary Suspect: ${analysis.primarySuspect}
            Confidence: ${analysis.confidence}

            Top Candidates:
            ${analysis.candidates.take(5).joinToString("\n") { "- ${it.title} (${it.score})" }}

            ## Evidence Summary
            - ${evidence.logClusters.size} log patterns analyzed
            - ${evidence.symptoms.size} symptoms detected
            - ${evidence.events.size} related events

            ## Task
            Create a report with:
            1. Executive summary (2-3 sentences)
            2. Recommended immediate actions
            3. Next investigation steps if needed
            4. Severity assessment (CRITICAL, HIGH, MEDIUM, LOW, NORMAL)

            Focus on actionable, specific recommendations.
        """.trimIndent()
        )

    /**
     * Condition: Evidence has been collected from Datadog.
     */
    @Condition(name = EVIDENCE_COLLECTED)
    fun hasEvidence(evidence: DatadogEvidence): Boolean =
        evidence.logClusters.isNotEmpty() || evidence.symptoms.isNotEmpty()

    /**
     * Condition: Root cause analysis is complete.
     */
    @Condition(name = ANALYSIS_COMPLETE)
    fun analysisComplete(analysis: RootCauseAnalysis): Boolean =
        analysis.candidates.isNotEmpty() && analysis.primarySuspect.isNotBlank()

    /**
     * Condition: Analysis is satisfactory.
     */
    @Condition(name = ANALYSIS_SATISFACTORY)
    fun analysisSatisfactory(critique: AnalysisCritique): Boolean =
        critique.accepted

    /**
     * Condition: Analysis needs improvement.
     */
    @Condition(name = ANALYSIS_UNSATISFACTORY)
    fun analysisUnsatisfactory(critique: AnalysisCritique): Boolean =
        !critique.accepted

    companion object {
        const val EVIDENCE_COLLECTED = "evidenceCollected"
        const val ANALYSIS_COMPLETE = "analysisComplete"
        const val ANALYSIS_SATISFACTORY = "analysisSatisfactory"
        const val ANALYSIS_UNSATISFACTORY = "analysisUnsatisfactory"
    }
}
