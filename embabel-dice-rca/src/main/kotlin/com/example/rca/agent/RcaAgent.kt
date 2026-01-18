package com.example.rca.agent

import com.example.rca.analysis.*
import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.DatadogException
import com.example.rca.datadog.dto.toLogEntry
import com.example.rca.datadog.dto.toSpanEntry
import com.example.rca.datadog.dto.toEventResponse
import com.example.rca.domain.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Embabel AI Agent for Root Cause Analysis.
 * 
 * This agent coordinates the analysis workflow:
 * 1. Collect data from Datadog (logs, metrics, spans, events)
 * 2. Analyze patterns and anomalies
 * 3. Score and rank candidates
 * 4. Generate recommendations
 * 5. Provide chat-based advice
 */
@Service
class RcaAgent(
    private val datadogClient: DatadogClient,
    private val logAnalyzer: LogAnalyzer,
    private val metricAnalyzer: MetricAnalyzer,
    private val apmAnalyzer: ApmAnalyzer,
    private val scoringEngine: ScoringEngine,
    private val chatAdvisor: ChatAdvisor
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Start analysis for an incident context.
     */
    fun startAnalysis(context: IncidentContext): Report {
        logger.info("Starting RCA analysis for incident: ${context.id}")

        try {
            // Step 1: Collect and analyze logs
            val logResult = analyzeLogs(context)
            
            // Step 2: Analyze APM data if scope is ready
            val apmResult = if (context.scope.isApmReady()) {
                analyzeApm(context)
            } else {
                null
            }

            // Step 3: Collect events
            val events = collectEvents(context)

            // Step 4: Score and rank candidates
            val logCandidates = scoringEngine.scoreLogClusters(logResult.clusters)
            val apmCandidates = apmResult?.topCandidates ?: emptyList()
            val allCandidates = scoringEngine.mergeAndRank(logCandidates, apmCandidates)

            // Update context with candidates
            allCandidates.forEach { context.addCandidate(it) }

            // Step 5: Generate recommendations
            val recommendations = scoringEngine.generateRecommendations(
                context.symptoms,
                allCandidates,
                events
            )

            // Build and return report
            val report = buildReport(context, logResult, apmResult, events, allCandidates, recommendations)

            logger.info("Completed RCA analysis for incident: ${context.id}, found ${allCandidates.size} candidates")

            return report
        } catch (e: Exception) {
            logger.error("Analysis failed for incident: ${context.id}", e)
            throw e
        }
    }

    /**
     * Analyze from a Datadog monitor.
     */
    fun analyzeFromMonitor(
        monitorId: Long,
        triggerTs: Instant?,
        windowMinutes: Int,
        baselineMinutes: Int
    ): Report {
        val anchor = triggerTs ?: Instant.now()
        val windows = Windows.endingAt(anchor, windowMinutes, baselineMinutes)

        val monitor = datadogClient.getMonitor(monitorId)
        val scope = Scope.fromMonitorTags(monitor.tags)
        val query = monitor.query

        // Create symptom from monitor metric
        val baselineMetrics = datadogClient.queryMetrics(query, windows.baseline.start, windows.baseline.end)
        val incidentMetrics = datadogClient.queryMetrics(query, windows.incident.start, windows.incident.end)
        val symptom = metricAnalyzer.analyzeMonitorMetric(query, baselineMetrics, incidentMetrics)

        val context = IncidentContext(
            id = "MON-$monitorId",
            windows = windows,
            scope = scope
        )
        context.addSymptom(symptom)
        context.metadata["monitor_id"] = monitorId
        context.metadata["monitor_name"] = monitor.name

        return startAnalysis(context)
    }

    /**
     * Analyze from a log query.
     */
    fun analyzeFromLogs(
        logQuery: String,
        anchorTs: Instant?,
        windowMinutes: Int,
        baselineMinutes: Int
    ): Report {
        val anchor = anchorTs ?: Instant.now()
        val windows = Windows.endingAt(anchor, windowMinutes, baselineMinutes)

        // Fetch logs to infer scope
        val incidentLogs = datadogClient.searchLogs(logQuery, windows.incident.start, windows.incident.end)
        val baselineLogs = datadogClient.searchLogs(logQuery, windows.baseline.start, windows.baseline.end)

        val scope = Scope.fromLogEntries(incidentLogs)

        // Create log volume symptom
        val symptom = metricAnalyzer.analyzeLogVolume(logQuery, baselineLogs.size, incidentLogs.size)

        val context = IncidentContext(
            id = "LOG-${System.currentTimeMillis()}",
            windows = windows,
            scope = scope
        )
        context.addSymptom(symptom)
        context.metadata["log_query"] = logQuery

        return startAnalysis(context)
    }

    /**
     * Analyze from service/env with explicit time range.
     */
    fun analyzeFromService(
        service: String,
        env: String,
        start: Instant,
        end: Instant,
        mode: AnalysisMode = AnalysisMode.LATENCY
    ): Report {
        val windows = Windows.fromRange(start, end)
        val scope = Scope(service = service, env = env)

        val symptom = metricAnalyzer.createServiceSymptom(datadogClient, scope, windows, mode)

        val context = IncidentContext(
            id = "SVC-${service.uppercase()}-${System.currentTimeMillis()}",
            windows = windows,
            scope = scope
        )
        context.addSymptom(symptom)
        context.metadata["mode"] = mode.name

        return startAnalysis(context)
    }

    /**
     * Handle chat message within an incident context.
     */
    fun chat(incidentId: String, message: String, context: IncidentContext): ChatResponse {
        return chatAdvisor.processMessage(message, context)
    }

    // Private analysis methods

    private fun analyzeLogs(context: IncidentContext): LogAnalysisResult {
        val query = buildLogQuery(context.scope)

        val incidentLogs = datadogClient.searchLogs(
            query,
            context.windows.incident.start,
            context.windows.incident.end
        )

        val baselineLogs = datadogClient.searchLogs(
            query,
            context.windows.baseline.start,
            context.windows.baseline.end
        )

        val clusters = logAnalyzer.clusterLogs(incidentLogs)
        val mergedClusters = logAnalyzer.mergeBaselineCounts(clusters, baselineLogs)
        val rankedClusters = logAnalyzer.rankClusters(mergedClusters, limit = 15)

        // Add log volume symptom if not already present
        if (context.symptoms.none { it.type == SymptomType.LOG_SIGNATURE }) {
            val symptom = metricAnalyzer.analyzeLogVolume(query, baselineLogs.size, incidentLogs.size)
            context.addSymptom(symptom)
        }

        return LogAnalysisResult(
            clusters = rankedClusters,
            totalIncidentLogs = incidentLogs.size,
            totalBaselineLogs = baselineLogs.size,
            newPatterns = rankedClusters.filter { it.isNewPattern },
            growthPatterns = rankedClusters.filter { it.growthRatio >= 2.0 }
        )
    }

    private fun analyzeApm(context: IncidentContext): ApmAnalysisResult? {
        val query = "service:${context.scope.service} env:${context.scope.env}"

        return try {
            val incidentSpans = datadogClient.searchSpans(
                query,
                context.windows.incident.start,
                context.windows.incident.end
            )

            val baselineSpans = datadogClient.searchSpans(
                query,
                context.windows.baseline.start,
                context.windows.baseline.end
            )

            val mode = context.metadata["mode"]?.toString()?.let { 
                AnalysisMode.valueOf(it) 
            } ?: AnalysisMode.LATENCY

            apmAnalyzer.analyzeSpans(incidentSpans, baselineSpans, mode)
        } catch (e: DatadogException) {
            logger.warn("APM analysis failed: ${e.message}")
            null
        }
    }

    private fun collectEvents(context: IncidentContext): List<com.example.rca.datadog.dto.EventEntry> {
        return try {
            val response = datadogClient.searchEvents(
                context.windows.incident.start,
                context.windows.incident.end,
                context.scope.toEventTagQuery()
            )
            response.events.take(20)
        } catch (e: DatadogException) {
            logger.warn("Event collection failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildLogQuery(scope: Scope): String {
        val parts = mutableListOf<String>()
        scope.service?.let { parts.add("service:$it") }
        scope.env?.let { parts.add("env:$it") }
        parts.add("(@status:error OR status:error OR level:error OR @level:error OR @http.status_code:[500 TO 599])")
        return parts.joinToString(" ")
    }

    private fun buildReport(
        context: IncidentContext,
        logResult: LogAnalysisResult,
        apmResult: ApmAnalysisResult?,
        events: List<com.example.rca.datadog.dto.EventEntry>,
        candidates: List<Candidate>,
        recommendations: List<String>
    ): Report {
        val findings = mutableMapOf<String, Any>(
            "log_query_used" to buildLogQuery(context.scope),
            "incident_log_count" to logResult.totalIncidentLogs,
            "baseline_log_count" to logResult.totalBaselineLogs,
            "log_clusters" to logResult.clusters.map { cluster ->
                mapOf(
                    "fingerprint" to cluster.fingerprint,
                    "template" to cluster.template,
                    "count_incident" to cluster.countIncident,
                    "count_baseline" to cluster.countBaseline,
                    "growth_ratio" to cluster.growthRatio,
                    "is_new_pattern" to cluster.isNewPattern
                )
            },
            "events" to events.map { event ->
                mapOf(
                    "ts" to event.dateHappened.toString(),
                    "title" to event.title,
                    "text" to event.text,
                    "tags" to event.tags
                )
            },
            "candidates" to candidates.map { c ->
                mapOf(
                    "kind" to c.kind.name.lowercase(),
                    "title" to c.title,
                    "score" to c.score,
                    "evidence" to c.evidence
                )
            }
        )

        if (apmResult != null) {
            findings["apm"] = apmResult.toMap()
        } else {
            findings["apm"] = mapOf(
                "enabled" to false,
                "reason" to "missing service/env or APM error"
            )
        }

        context.metadata["monitor_id"]?.let { findings["monitor_id"] = it }
        context.metadata["monitor_name"]?.let { findings["monitor_name"] = it }

        return Report(
            meta = ReportMeta(
                seedType = inferSeedType(context),
                generatedAt = Instant.now(),
                ddSite = "datadoghq.com",  // Would come from config
                input = context.metadata.toMap()
            ),
            windows = context.windows,
            scope = context.scope,
            symptoms = context.symptoms.toList(),
            findings = findings,
            recommendations = recommendations,
            candidates = candidates
        )
    }

    private fun inferSeedType(context: IncidentContext): SeedType {
        return when {
            context.id.startsWith("MON-") -> SeedType.MONITOR
            context.id.startsWith("LOG-") -> SeedType.LOGS
            context.id.startsWith("SVC-") -> SeedType.SERVICE
            context.id.startsWith("INC-") -> SeedType.ALERT
            else -> SeedType.MANUAL
        }
    }
}
