/*
 * Embabel RCA Agent for Datadog Incident Investigation
 *
 * This agent uses the Embabel framework to provide AI-powered
 * root cause analysis for production incidents, using Datadog MCP tools.
 */
package com.example.rca.agent

import com.embabel.agent.api.annotation.*
import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.create
import com.embabel.agent.domain.io.UserInput
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.model.ModelProvider.Companion.CHEAPEST_ROLE
import com.example.rca.domain.*
import com.example.rca.mcp.DatadogTools
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
 * Evidence collected from Datadog via MCP tools.
 */
data class DatadogEvidence(
    val logsSearched: Boolean,
    val logPatterns: List<String>,
    val errorCount: Int,
    val metricsAnalyzed: Boolean,
    val latencyChange: String?,
    val errorRateChange: String?,
    val tracesSearched: Boolean,
    val slowEndpoints: List<String>,
    val failingDependencies: List<String>,
    val eventsFound: List<String>,
    val recentDeployments: Int,
    val summary: String,
)

/**
 * Analysis of the root cause candidates.
 */
data class RootCauseAnalysis(
    val candidates: List<RootCauseCandidate>,
    val primarySuspect: String,
    val confidence: Double,
    val reasoning: String,
)

data class RootCauseCandidate(
    val title: String,
    val category: String,
    val score: Double,
    val evidence: String,
)

/**
 * Final investigation report with recommendations.
 */
data class InvestigationReport(
    val summary: String,
    val rootCause: RootCauseAnalysis,
    val recommendations: List<String>,
    val nextSteps: List<String>,
    val severity: String,
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
 * This agent uses Datadog MCP tools to gather evidence and AI reasoning
 * to identify root causes. The workflow:
 *
 * 1. Categorize incident from user description
 * 2. Collect evidence using Datadog MCP tools (logs, metrics, traces, events)
 * 3. Analyze patterns and identify root cause candidates
 * 4. Self-critique for quality assurance
 * 5. Generate actionable recommendations
 *
 * Tools available during investigation:
 * - datadog_search_logs: Find error patterns
 * - datadog_query_metrics: Analyze performance data
 * - datadog_search_traces: Examine request flow and dependencies
 * - datadog_get_events: Find recent deployments and changes
 * - datadog_compare_periods: Compare incident vs baseline
 */
@Agent(
    description = "Investigate production incidents and identify root causes using Datadog MCP tools",
)
class IncidentInvestigatorAgent(
    val properties: RcaAgentProperties,
    val datadogTools: DatadogTools,  // Injected for tool registration
) {
    private val logger = LoggerFactory.getLogger(IncidentInvestigatorAgent::class.java)

    init {
        logger.info("IncidentInvestigatorAgent initialized with Datadog MCP tools")
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
     * Collect evidence from Datadog using MCP tools.
     * The LLM decides which tools to call based on the incident context.
     */
    @Action(
        post = [EVIDENCE_COLLECTED],
    )
    fun collectDatadogEvidence(
        request: IncidentRequest,
        categorization: IncidentCategorization,
        context: OperationContext,
    ): DatadogEvidence = context.ai()
        .withLlm(properties.analysisModel)
        .create(
            """
            You are investigating a production incident. Use the Datadog tools to collect evidence.

            ## Incident Context
            - Service: ${request.service ?: "unknown (search broadly)"}
            - Environment: ${request.env ?: "prod"}
            - Category: ${categorization.category}
            - Description: ${request.description}
            - Time window: Last ${properties.defaultWindowMinutes} minutes

            ## Available Tools
            Use these Datadog MCP tools to gather evidence:

            1. **datadog_search_logs** - Search for error logs and patterns
               Call with: service, env, query like "@status:error"

            2. **datadog_query_metrics** - Get latency, error rate, throughput metrics
               Call with: metric queries like "avg:trace.request.duration{service:X}"

            3. **datadog_search_traces** - Analyze APM traces for slow endpoints and dependencies
               Call with: service, env, optionally errorsOnly=true

            4. **datadog_get_events** - Find recent deployments, config changes, alerts
               Call with: service, env, eventType like "deploy"

            5. **datadog_compare_periods** - Compare incident metrics vs baseline
               Call with: metric query, incidentMinutes, baselineMinutes

            ## Your Task
            1. Search logs for errors related to this incident
            2. Query relevant metrics (latency for LATENCY issues, error counts for ERROR_RATE)
            3. Search traces to identify slow endpoints or failing dependencies
            4. Check for recent deployments or config changes
            5. Compare current metrics against baseline

            Based on the tool results, summarize what you found as DatadogEvidence.
        """.trimIndent()
        )

    /**
     * Analyze collected evidence to identify root cause candidates.
     * Uses the advanced model for deep reasoning with access to tools for follow-up queries.
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
            Analyze the collected evidence to identify root cause candidates.

            ## Incident Context
            - Service: ${request.service ?: "unknown"}
            - Environment: ${request.env ?: "unknown"}
            - Category: ${categorization.category}
            - Description: ${request.description}

            ## Evidence Collected
            ${evidence.summary}

            ### Log Patterns
            ${evidence.logPatterns.joinToString("\n") { "- $it" }}
            Error count: ${evidence.errorCount}

            ### Metrics
            - Latency change: ${evidence.latencyChange ?: "not measured"}
            - Error rate change: ${evidence.errorRateChange ?: "not measured"}

            ### Traces
            Slow endpoints: ${evidence.slowEndpoints.joinToString(", ")}
            Failing dependencies: ${evidence.failingDependencies.joinToString(", ")}

            ### Events
            ${evidence.eventsFound.joinToString("\n") { "- $it" }}
            Recent deployments: ${evidence.recentDeployments}

            ## Your Task
            Based on this evidence:
            1. Identify up to ${properties.maxCandidates} root cause candidates
            2. Score each by likelihood (0.0 to 1.0)
            3. Identify the PRIMARY suspect with highest confidence
            4. Explain your reasoning with specific evidence references

            You can use Datadog tools for follow-up queries if needed.

            Categories for candidates:
            - DEPENDENCY: Downstream service issue
            - INFRASTRUCTURE: Database, cache, connection pool
            - DEPLOYMENT: Recent code or config change
            - EXTERNAL: Third-party API or service
            - RESOURCE: Memory, CPU, disk exhaustion
            - CODE: Bug or logic error
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
            ${evidence.summary}

            ## Evaluation Criteria
            1. Does the analysis address the reported symptoms?
            2. Is the primary suspect well-supported by evidence?
            3. Are alternative hypotheses considered?
            4. Is the confidence level appropriate given the evidence?
            5. Would the recommendations lead to resolution?

            Accept if it provides actionable insights with reasonable confidence.
            Reject if too vague, unsupported by evidence, or missing obvious candidates.
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
            ${analysis.candidates.take(5).joinToString("\n") { "- ${it.title}: ${it.evidence}" }}

            ## Evidence Summary
            ${evidence.summary}

            ## Create Report With:
            1. **summary**: Executive summary (2-3 sentences)
            2. **recommendations**: Specific, actionable steps (immediate + long-term)
            3. **nextSteps**: What to investigate if this doesn't resolve it
            4. **severity**: CRITICAL, HIGH, MEDIUM, LOW, or NORMAL

            Be specific. Reference actual error messages, metrics, and deployments found.
        """.trimIndent()
        )

    /**
     * Condition: Evidence has been collected from Datadog.
     */
    @Condition(name = EVIDENCE_COLLECTED)
    fun hasEvidence(evidence: DatadogEvidence): Boolean =
        evidence.logsSearched || evidence.metricsAnalyzed || evidence.tracesSearched

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
