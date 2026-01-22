package com.example.rca.dice

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.dto.*
import com.example.rca.dice.model.*
import com.example.rca.domain.*
import com.example.rca.mock.MockDatadogClient
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Test framework for validating DICE-based RCA conclusions.
 * 
 * This framework:
 * 1. Ingests PRIOR KNOWLEDGE into DICE (system architecture, dependencies, past incidents)
 * 2. Simulates a NEW ALERT/INCIDENT
 * 3. Verifies the RCA agent reaches the CORRECT CONCLUSION using DICE + Datadog data
 * 
 * Uses:
 * - DICE API: POST /api/v1/contexts/{contextId}/ingest, /query, /memory/search
 * - Datadog API: GET /api/v1/query, POST /api/v2/logs/events/search, POST /api/v2/spans/events/search
 */
class DiceKnowledgeTestFramework(
    private val diceClient: DiceClient,
    private val datadogClient: DatadogClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Load prior knowledge into DICE for a test context.
     * This represents what the system "knows" before the incident.
     * 
     * Uses DICE API: POST /api/v1/contexts/{contextId}/ingest
     */
    fun loadPriorKnowledge(contextId: String, knowledge: PriorKnowledge): LoadResult {
        logger.info("Loading prior knowledge into DICE context: $contextId")
        
        val results = mutableListOf<IngestResponse>()
        
        // 1. Ingest system architecture
        knowledge.architecture?.let { arch ->
            val archText = formatArchitecture(arch)
            val metadata = mapOf("type" to "architecture", "name" to arch.name)
            results.add(diceClient.ingest(contextId, "architecture", archText, metadata))
        }
        
        // 2. Ingest service dependencies
        knowledge.dependencies.forEach { dep ->
            val depText = formatDependency(dep)
            val metadata = mapOf("type" to "dependency", "from" to dep.from, "to" to dep.to)
            results.add(diceClient.ingest(contextId, "dep-${dep.from}-${dep.to}", depText, metadata))
        }
        
        // 3. Ingest known failure patterns
        knowledge.failurePatterns.forEach { pattern ->
            val patternText = formatFailurePattern(pattern)
            val metadata = mapOf("type" to "failure_pattern", "patternId" to pattern.id)
            results.add(diceClient.ingest(contextId, "pattern-${pattern.id}", patternText, metadata))
        }
        
        // 4. Ingest past incidents (historical knowledge)
        knowledge.pastIncidents.forEach { incident ->
            val incidentText = formatPastIncident(incident)
            val metadata = mapOf("type" to "past_incident", "incidentId" to incident.id)
            results.add(diceClient.ingest(contextId, "incident-${incident.id}", incidentText, metadata))
        }
        
        // 5. Ingest runbooks
        knowledge.runbooks.forEach { runbook ->
            val runbookText = formatRunbook(runbook)
            val metadata = mapOf("type" to "runbook", "runbookId" to runbook.id)
            results.add(diceClient.ingest(contextId, "runbook-${runbook.id}", runbookText, metadata))
        }
        
        // 6. Ingest SLOs and thresholds
        knowledge.slos.forEach { slo ->
            val sloText = formatSlo(slo)
            val metadata = mapOf("type" to "slo", "sloId" to slo.id, "service" to slo.service)
            results.add(diceClient.ingest(contextId, "slo-${slo.id}", sloText, metadata))
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
     * 
     * This method:
     * 1. Ingests alert into DICE (POST /api/v1/contexts/{contextId}/ingest)
     * 2. Queries DICE for initial assessment (POST /api/v1/contexts/{contextId}/query)
     * 3. Gathers evidence from Datadog APIs
     * 4. Ingests evidence into DICE
     * 5. Queries DICE for root cause conclusion
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
        
        // Step 1: Ingest the alert into DICE
        // DICE API: POST /api/v1/contexts/{contextId}/ingest
        val alertText = formatTestAlert(alert)
        val alertMetadata = mapOf(
            "type" to "alert",
            "alertId" to alert.id,
            "service" to (alert.service ?: "unknown"),
            "severity" to alert.severity
        )
        diceClient.ingest(contextId, "alert-${alert.id}", alertText, alertMetadata)
        
        // Step 2: Query DICE for initial assessment based on prior knowledge
        // DICE API: POST /api/v1/contexts/{contextId}/query
        val initialAssessment = diceClient.query(
            contextId,
            "Given this alert and our system knowledge, what are the most likely causes? " +
            "What should we investigate first?"
        )
        
        // Step 3: Search DICE for relevant failure patterns
        // DICE API: POST /api/v1/contexts/{contextId}/memory/search
        val relevantPatterns = diceClient.searchPropositions(
            contextId,
            query = "failure pattern ${alert.service ?: ""} ${alert.message}",
            topK = 5
        )
        logger.info("Found ${relevantPatterns.size} relevant patterns in DICE")
        
        // Step 4: Gather evidence from Datadog APIs
        val evidence = gatherEvidence(alert, contextId)
        
        // Step 5: Ingest evidence into DICE
        evidence.forEach { (key, text) ->
            val evidenceMetadata = mapOf("type" to "evidence", "source" to "datadog")
            diceClient.ingest(contextId, "evidence-$key", text, evidenceMetadata)
        }
        
        // Step 6: Query DICE for root cause conclusion
        // DICE API: POST /api/v1/contexts/{contextId}/query
        val rootCauseAnalysis = diceClient.query(
            contextId,
            "Based on all the evidence gathered, what is the root cause of this incident? " +
            "Explain your reasoning step by step."
        )
        
        // Step 7: Get recommendations
        val recommendations = diceClient.query(
            contextId,
            "What are the recommended actions to resolve this incident and prevent recurrence?"
        )
        
        // Step 8: List all propositions for verification
        // DICE API: GET /api/v1/contexts/{contextId}/memory
        val allPropositions = diceClient.listPropositions(contextId, limit = 100)
        
        return AnalysisResult(
            alertId = alert.id,
            initialAssessment = initialAssessment,
            relevantPatterns = relevantPatterns,
            evidenceGathered = evidence.keys.toList(),
            rootCauseAnalysis = rootCauseAnalysis,
            recommendations = recommendations,
            propositions = allPropositions
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

    /**
     * Gather evidence from Datadog APIs.
     * 
     * Datadog APIs used:
     * - GET /api/v1/query - Query timeseries metrics
     * - POST /api/v2/logs/events/search - Search logs
     * - POST /api/v2/spans/events/search - Search APM spans
     * - GET /api/v1/events - Query events
     */
    private fun gatherEvidence(alert: TestAlert, contextId: String): Map<String, String> {
        val evidence = mutableMapOf<String, String>()
        val now = Instant.now()
        val start = now.minus(Duration.ofHours(1))
        
        // 1. Query metrics using Datadog Metrics API
        // Datadog API: GET /api/v1/query?from={epoch}&to={epoch}&query={query}
        alert.metricsToQuery.forEach { query ->
            try {
                logger.info("Querying Datadog metrics: $query")
                val response: MetricResponse = datadogClient.queryMetrics(query, start, now)
                evidence["metric-${query.hashCode().toString(16)}"] = formatMetricEvidence(query, response)
            } catch (e: Exception) {
                logger.warn("Failed to query Datadog metric: $query - ${e.message}")
            }
        }
        
        // 2. Search logs using Datadog Logs API
        // Datadog API: POST /api/v2/logs/events/search
        // Body: { filter: { from, to, query }, sort, page: { limit, cursor } }
        alert.logQueries.forEach { query ->
            try {
                logger.info("Searching Datadog logs: $query")
                val logs: List<LogEntry> = datadogClient.searchLogs(
                    query = query,
                    start = start,
                    end = now,
                    limit = 100,
                    maxPages = 1
                )
                evidence["logs-${query.hashCode().toString(16)}"] = formatLogEvidence(query, logs)
            } catch (e: Exception) {
                logger.warn("Failed to search Datadog logs: $query - ${e.message}")
            }
        }
        
        // 3. Search APM traces/spans using Datadog APM API
        // Datadog API: POST /api/v2/spans/events/search
        // Body: { filter: { from, to, query }, sort, page: { limit, cursor } }
        alert.traceQueries.forEach { (service, env) ->
            try {
                val spanQuery = "service:$service env:$env"
                logger.info("Searching Datadog spans: $spanQuery")
                val spans: List<SpanEntry> = datadogClient.searchSpans(
                    query = spanQuery,
                    start = start,
                    end = now,
                    limit = 100,
                    maxPages = 1
                )
                evidence["traces-$service"] = formatTraceEvidence(service, spans)
            } catch (e: Exception) {
                logger.warn("Failed to search Datadog spans: $service - ${e.message}")
            }
        }
        
        // 4. Query events using Datadog Events API
        // Datadog API: GET /api/v1/events?start={epoch}&end={epoch}&tags={tags}
        try {
            val tags = alert.service?.let { "service:$it" }
            logger.info("Querying Datadog events: tags=$tags")
            val events: EventResponse = datadogClient.searchEvents(start, now, tags)
            evidence["events"] = formatEventEvidence(events)
        } catch (e: Exception) {
            logger.warn("Failed to query Datadog events: ${e.message}")
        }
        
        // 5. Get monitor details if monitorId provided
        // Datadog API: GET /api/v1/monitor/{monitorId}
        alert.monitorId?.let { monitorId ->
            try {
                logger.info("Getting Datadog monitor: $monitorId")
                val monitor: MonitorResponse = datadogClient.getMonitor(monitorId)
                evidence["monitor-$monitorId"] = formatMonitorEvidence(monitor)
            } catch (e: Exception) {
                logger.warn("Failed to get Datadog monitor: $monitorId - ${e.message}")
            }
        }
        
        logger.info("Gathered ${evidence.size} pieces of evidence from Datadog")
        return evidence
    }
    
    private fun formatMonitorEvidence(monitor: MonitorResponse): String = buildString {
        appendLine("MONITOR DETAILS")
        appendLine("ID: ${monitor.id}")
        appendLine("Name: ${monitor.name}")
        appendLine("Type: ${monitor.type}")
        appendLine("Query: ${monitor.query}")
        appendLine("Tags: ${monitor.tags.joinToString(", ")}")
        monitor.state?.let { 
            appendLine("State: ${it.overallState}")
        }
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
    val env: String? = "prod",
    val message: String,
    val monitorId: Long? = null,
    val metricQuery: String? = null,
    val currentValue: Double? = null,
    val threshold: Double? = null,
    val metricsToQuery: List<String> = emptyList(),
    val logQueries: List<String> = emptyList(),
    val traceQueries: List<Pair<String, String>> = emptyList() // (service, env)
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
    val relevantPatterns: List<DiceProposition> = emptyList(),
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
