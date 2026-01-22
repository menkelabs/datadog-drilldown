package com.example.rca.dice

import com.example.rca.datadog.DatadogClient
import com.example.rca.dice.model.*
import com.example.rca.domain.*
import com.example.rca.mock.MockDatadogClient
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Test framework for validating DICE-based RCA conclusions.
 * 
 * This framework:
 * 1. Ingests PRIOR KNOWLEDGE into DICE (system architecture, dependencies, past incidents)
 * 2. Simulates a NEW ALERT/INCIDENT
 * 3. Verifies the RCA agent reaches the CORRECT CONCLUSION using DICE + Datadog data
 */
class DiceKnowledgeTestFramework(
    private val diceClient: DiceClient,
    private val datadogClient: DatadogClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Load prior knowledge into DICE for a test context.
     * This represents what the system "knows" before the incident.
     */
    fun loadPriorKnowledge(contextId: String, knowledge: PriorKnowledge): LoadResult {
        logger.info("Loading prior knowledge into DICE context: $contextId")
        
        val results = mutableListOf<IngestResponse>()
        
        // 1. Ingest system architecture
        knowledge.architecture?.let { arch ->
            val archText = formatArchitecture(arch)
            results.add(diceClient.ingest(contextId, "architecture", archText))
        }
        
        // 2. Ingest service dependencies
        knowledge.dependencies.forEach { dep ->
            val depText = formatDependency(dep)
            results.add(diceClient.ingest(contextId, "dep-${dep.from}-${dep.to}", depText))
        }
        
        // 3. Ingest known failure patterns
        knowledge.failurePatterns.forEach { pattern ->
            val patternText = formatFailurePattern(pattern)
            results.add(diceClient.ingest(contextId, "pattern-${pattern.id}", patternText))
        }
        
        // 4. Ingest past incidents (historical knowledge)
        knowledge.pastIncidents.forEach { incident ->
            val incidentText = formatPastIncident(incident)
            results.add(diceClient.ingest(contextId, "incident-${incident.id}", incidentText))
        }
        
        // 5. Ingest runbooks
        knowledge.runbooks.forEach { runbook ->
            val runbookText = formatRunbook(runbook)
            results.add(diceClient.ingest(contextId, "runbook-${runbook.id}", runbookText))
        }
        
        // 6. Ingest SLOs and thresholds
        knowledge.slos.forEach { slo ->
            val sloText = formatSlo(slo)
            results.add(diceClient.ingest(contextId, "slo-${slo.id}", sloText))
        }
        
        val successCount = results.count { it.status == "SUCCESS" }
        val totalProps = results.sumOf { it.propositionsExtracted }
        
        logger.info("Loaded $successCount/${results.size} documents, extracted $totalProps propositions")
        
        return LoadResult(
            contextId = contextId,
            documentsLoaded = successCount,
            totalDocuments = results.size,
            propositionsExtracted = totalProps
        )
    }

    /**
     * Simulate a new alert and run RCA analysis.
     */
    fun simulateAlert(
        contextId: String,
        alert: TestAlert,
        mockScenario: String? = null
    ): AnalysisResult {
        logger.info("Simulating alert: ${alert.name} for context: $contextId")
        
        // If using MockDatadogClient, set the scenario
        if (datadogClient is MockDatadogClient && mockScenario != null) {
            datadogClient.setActiveScenario(mockScenario)
        }
        
        // Ingest the alert into DICE
        val alertText = formatTestAlert(alert)
        diceClient.ingest(contextId, "alert-${alert.id}", alertText)
        
        // Query DICE for initial assessment
        val initialAssessment = diceClient.query(
            contextId,
            "Given this alert and our system knowledge, what are the most likely causes? " +
            "What should we investigate first?"
        )
        
        // Simulate gathering evidence from Datadog
        val evidence = gatherEvidence(alert, contextId)
        
        // Ingest evidence into DICE
        evidence.forEach { (key, text) ->
            diceClient.ingest(contextId, "evidence-$key", text)
        }
        
        // Query DICE for root cause conclusion
        val rootCauseAnalysis = diceClient.query(
            contextId,
            "Based on all the evidence gathered, what is the root cause of this incident? " +
            "Explain your reasoning step by step."
        )
        
        // Get recommendations
        val recommendations = diceClient.query(
            contextId,
            "What are the recommended actions to resolve this incident and prevent recurrence?"
        )
        
        return AnalysisResult(
            alertId = alert.id,
            initialAssessment = initialAssessment,
            evidenceGathered = evidence.keys.toList(),
            rootCauseAnalysis = rootCauseAnalysis,
            recommendations = recommendations,
            propositions = diceClient.listPropositions(contextId)
        )
    }

    /**
     * Verify that the analysis reached the expected conclusion.
     */
    fun verifyConclusion(
        result: AnalysisResult,
        expectedRootCause: ExpectedRootCause
    ): VerificationResult {
        logger.info("Verifying conclusion for alert: ${result.alertId}")
        
        val rootCauseText = result.rootCauseAnalysis.lowercase()
        
        // Check if expected root cause keywords are present
        val keywordsFound = expectedRootCause.keywords.filter { keyword ->
            rootCauseText.contains(keyword.lowercase())
        }
        
        val keywordCoverage = keywordsFound.size.toDouble() / expectedRootCause.keywords.size
        
        // Check if the analysis identified the correct component
        val componentIdentified = expectedRootCause.component?.let { component ->
            rootCauseText.contains(component.lowercase())
        } ?: true
        
        // Check if the analysis identified the correct cause type
        val causeTypeIdentified = expectedRootCause.causeType?.let { causeType ->
            rootCauseText.contains(causeType.lowercase())
        } ?: true
        
        val passed = keywordCoverage >= expectedRootCause.requiredKeywordCoverage &&
                     componentIdentified &&
                     causeTypeIdentified
        
        return VerificationResult(
            passed = passed,
            keywordsFound = keywordsFound,
            keywordsMissing = expectedRootCause.keywords - keywordsFound.toSet(),
            keywordCoverage = keywordCoverage,
            componentIdentified = componentIdentified,
            causeTypeIdentified = causeTypeIdentified,
            actualRootCause = result.rootCauseAnalysis,
            expectedKeywords = expectedRootCause.keywords
        )
    }

    // Evidence gathering from Datadog
    private fun gatherEvidence(alert: TestAlert, contextId: String): Map<String, String> {
        val evidence = mutableMapOf<String, String>()
        val now = Instant.now()
        val start = now.minusSeconds(3600) // 1 hour back
        
        // Gather metrics
        alert.metricsToQuery.forEach { query ->
            try {
                val response = datadogClient.queryMetrics(query, start, now)
                evidence["metric-${query.hashCode()}"] = formatMetricEvidence(query, response)
            } catch (e: Exception) {
                logger.warn("Failed to query metric: $query", e)
            }
        }
        
        // Gather logs
        alert.logQueries.forEach { query ->
            try {
                val logs = datadogClient.searchLogs(query, start, now)
                evidence["logs-${query.hashCode()}"] = formatLogEvidence(query, logs)
            } catch (e: Exception) {
                logger.warn("Failed to query logs: $query", e)
            }
        }
        
        // Gather traces
        alert.traceQueries.forEach { (service, env) ->
            try {
                val spans = datadogClient.searchSpans("service:$service env:$env", start, now)
                evidence["traces-$service"] = formatTraceEvidence(service, spans)
            } catch (e: Exception) {
                logger.warn("Failed to query traces: $service", e)
            }
        }
        
        // Gather events
        try {
            val events = datadogClient.searchEvents(start, now, alert.service?.let { "service:$it" })
            evidence["events"] = formatEventEvidence(events)
        } catch (e: Exception) {
            logger.warn("Failed to query events", e)
        }
        
        return evidence
    }

    // Formatting helpers
    private fun formatArchitecture(arch: SystemArchitecture): String = buildString {
        appendLine("SYSTEM ARCHITECTURE")
        appendLine("==================")
        appendLine("System: ${arch.name}")
        appendLine("Description: ${arch.description}")
        appendLine()
        appendLine("Services:")
        arch.services.forEach { service ->
            appendLine("- ${service.name}: ${service.description}")
            appendLine("  Type: ${service.type}")
            appendLine("  Dependencies: ${service.dependencies.joinToString(", ")}")
            appendLine("  Critical: ${service.critical}")
        }
    }

    private fun formatDependency(dep: ServiceDependency): String = buildString {
        appendLine("SERVICE DEPENDENCY")
        appendLine("From: ${dep.from}")
        appendLine("To: ${dep.to}")
        appendLine("Type: ${dep.type}")
        appendLine("Critical: ${dep.critical}")
        dep.notes?.let { appendLine("Notes: $it") }
    }

    private fun formatFailurePattern(pattern: FailurePattern): String = buildString {
        appendLine("KNOWN FAILURE PATTERN: ${pattern.name}")
        appendLine("================================")
        appendLine("ID: ${pattern.id}")
        appendLine("Description: ${pattern.description}")
        appendLine()
        appendLine("Symptoms:")
        pattern.symptoms.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Root Cause: ${pattern.rootCause}")
        appendLine()
        appendLine("Resolution: ${pattern.resolution}")
        appendLine()
        appendLine("Affected Services: ${pattern.affectedServices.joinToString(", ")}")
    }

    private fun formatPastIncident(incident: PastIncident): String = buildString {
        appendLine("PAST INCIDENT: ${incident.title}")
        appendLine("==============================")
        appendLine("ID: ${incident.id}")
        appendLine("Date: ${incident.date}")
        appendLine("Duration: ${incident.durationMinutes} minutes")
        appendLine("Severity: ${incident.severity}")
        appendLine()
        appendLine("Symptoms:")
        incident.symptoms.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Root Cause: ${incident.rootCause}")
        appendLine()
        appendLine("Resolution: ${incident.resolution}")
        appendLine()
        appendLine("Lessons Learned:")
        incident.lessonsLearned.forEach { appendLine("- $it") }
    }

    private fun formatRunbook(runbook: Runbook): String = buildString {
        appendLine("RUNBOOK: ${runbook.title}")
        appendLine("========================")
        appendLine("ID: ${runbook.id}")
        appendLine("Trigger: ${runbook.trigger}")
        appendLine()
        appendLine("Steps:")
        runbook.steps.forEachIndexed { i, step ->
            appendLine("${i + 1}. $step")
        }
        appendLine()
        appendLine("Escalation: ${runbook.escalation}")
    }

    private fun formatSlo(slo: ServiceLevelObjective): String = buildString {
        appendLine("SLO: ${slo.name}")
        appendLine("Service: ${slo.service}")
        appendLine("Metric: ${slo.metric}")
        appendLine("Target: ${slo.target}")
        appendLine("Window: ${slo.windowDays} days")
    }

    private fun formatTestAlert(alert: TestAlert): String = buildString {
        appendLine("ALERT TRIGGERED")
        appendLine("===============")
        appendLine("ID: ${alert.id}")
        appendLine("Name: ${alert.name}")
        appendLine("Time: ${alert.timestamp}")
        appendLine("Severity: ${alert.severity}")
        appendLine("Service: ${alert.service ?: "N/A"}")
        appendLine("Message: ${alert.message}")
        appendLine()
        appendLine("Metric Query: ${alert.metricQuery ?: "N/A"}")
        appendLine("Current Value: ${alert.currentValue ?: "N/A"}")
        appendLine("Threshold: ${alert.threshold ?: "N/A"}")
    }

    private fun formatMetricEvidence(query: String, response: com.example.rca.datadog.dto.MetricResponse): String = buildString {
        appendLine("METRIC EVIDENCE")
        appendLine("Query: $query")
        appendLine("Series count: ${response.series.size}")
        response.series.forEach { series ->
            appendLine("- ${series.metric}: ${series.pointlist.size} points")
            if (series.pointlist.isNotEmpty()) {
                val avg = series.pointlist.map { it.value }.average()
                val max = series.pointlist.maxOf { it.value }
                appendLine("  Average: $avg, Max: $max")
            }
        }
    }

    private fun formatLogEvidence(query: String, logs: List<com.example.rca.datadog.dto.LogEntry>): String = buildString {
        appendLine("LOG EVIDENCE")
        appendLine("Query: $query")
        appendLine("Total logs: ${logs.size}")
        appendLine()
        val errorLogs = logs.filter { it.isError() }
        appendLine("Error logs: ${errorLogs.size}")
        appendLine()
        appendLine("Sample errors:")
        errorLogs.take(5).forEach { log ->
            appendLine("- [${log.timestamp}] ${log.errorType() ?: "ERROR"}: ${log.message.take(200)}")
        }
    }

    private fun formatTraceEvidence(service: String, spans: List<com.example.rca.datadog.dto.SpanEntry>): String = buildString {
        appendLine("TRACE EVIDENCE for $service")
        appendLine("Total spans: ${spans.size}")
        val errorSpans = spans.filter { it.isError }
        appendLine("Error spans: ${errorSpans.size}")
        
        val byResource = spans.groupBy { it.resource }
        appendLine()
        appendLine("By endpoint:")
        byResource.entries.sortedByDescending { it.value.size }.take(5).forEach { (resource, resourceSpans) ->
            val avgDuration = resourceSpans.map { it.durationMs }.average()
            val errorRate = resourceSpans.count { it.isError }.toDouble() / resourceSpans.size
            appendLine("- $resource: ${resourceSpans.size} spans, avg=${avgDuration.toInt()}ms, errors=${(errorRate*100).toInt()}%")
        }
    }

    private fun formatEventEvidence(events: com.example.rca.datadog.dto.EventResponse): String = buildString {
        appendLine("EVENT EVIDENCE")
        appendLine("Total events: ${events.events.size}")
        appendLine()
        val deployments = events.events.filter { it.isDeployment() }
        if (deployments.isNotEmpty()) {
            appendLine("Deployments:")
            deployments.forEach { appendLine("- [${it.dateHappened}] ${it.title}") }
        }
        appendLine()
        val configChanges = events.events.filter { it.isConfigChange() }
        if (configChanges.isNotEmpty()) {
            appendLine("Config changes:")
            configChanges.forEach { appendLine("- [${it.dateHappened}] ${it.title}") }
        }
    }
}

// Data classes for the framework

data class PriorKnowledge(
    val architecture: SystemArchitecture? = null,
    val dependencies: List<ServiceDependency> = emptyList(),
    val failurePatterns: List<FailurePattern> = emptyList(),
    val pastIncidents: List<PastIncident> = emptyList(),
    val runbooks: List<Runbook> = emptyList(),
    val slos: List<ServiceLevelObjective> = emptyList()
)

data class SystemArchitecture(
    val name: String,
    val description: String,
    val services: List<ServiceInfo>
)

data class ServiceInfo(
    val name: String,
    val description: String,
    val type: String,
    val dependencies: List<String>,
    val critical: Boolean = false
)

data class ServiceDependency(
    val from: String,
    val to: String,
    val type: String, // http, grpc, database, cache, queue
    val critical: Boolean = true,
    val notes: String? = null
)

data class FailurePattern(
    val id: String,
    val name: String,
    val description: String,
    val symptoms: List<String>,
    val rootCause: String,
    val resolution: String,
    val affectedServices: List<String>
)

data class PastIncident(
    val id: String,
    val title: String,
    val date: String,
    val durationMinutes: Int,
    val severity: String,
    val symptoms: List<String>,
    val rootCause: String,
    val resolution: String,
    val lessonsLearned: List<String>
)

data class Runbook(
    val id: String,
    val title: String,
    val trigger: String,
    val steps: List<String>,
    val escalation: String
)

data class ServiceLevelObjective(
    val id: String,
    val name: String,
    val service: String,
    val metric: String,
    val target: String,
    val windowDays: Int
)

data class TestAlert(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Instant = Instant.now(),
    val severity: String = "WARNING",
    val service: String? = null,
    val message: String,
    val metricQuery: String? = null,
    val currentValue: Double? = null,
    val threshold: Double? = null,
    val metricsToQuery: List<String> = emptyList(),
    val logQueries: List<String> = emptyList(),
    val traceQueries: List<Pair<String, String>> = emptyList() // service, env
)

data class ExpectedRootCause(
    val keywords: List<String>,
    val component: String? = null,
    val causeType: String? = null,
    val requiredKeywordCoverage: Double = 0.6
)

data class LoadResult(
    val contextId: String,
    val documentsLoaded: Int,
    val totalDocuments: Int,
    val propositionsExtracted: Int
)

data class AnalysisResult(
    val alertId: String,
    val initialAssessment: String,
    val evidenceGathered: List<String>,
    val rootCauseAnalysis: String,
    val recommendations: String,
    val propositions: List<DiceProposition>
)

data class VerificationResult(
    val passed: Boolean,
    val keywordsFound: List<String>,
    val keywordsMissing: List<String>,
    val keywordCoverage: Double,
    val componentIdentified: Boolean,
    val causeTypeIdentified: Boolean,
    val actualRootCause: String,
    val expectedKeywords: List<String>
) {
    fun summary(): String = buildString {
        appendLine("Verification ${if (passed) "PASSED" else "FAILED"}")
        appendLine("Keyword coverage: ${(keywordCoverage * 100).toInt()}%")
        appendLine("Keywords found: $keywordsFound")
        appendLine("Keywords missing: $keywordsMissing")
        appendLine("Component identified: $componentIdentified")
        appendLine("Cause type identified: $causeTypeIdentified")
    }
}
