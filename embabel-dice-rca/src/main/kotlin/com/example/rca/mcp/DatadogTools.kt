/*
 * Datadog MCP Tools for Embabel Agent
 *
 * These tools allow the LLM agent to query Datadog APIs directly
 * during incident investigation.
 */
package com.example.rca.mcp

import com.embabel.agent.api.annotation.LlmTool
import com.embabel.agent.api.annotation.LlmTool.Param
import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.dto.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Datadog tools for LLM-driven incident investigation.
 *
 * These tools are exposed to the Embabel agent and can be called
 * by the LLM to gather evidence from Datadog during analysis.
 */
@Component
class DatadogTools(
    private val datadogClient: DatadogClient,
) {
    private val logger = LoggerFactory.getLogger(DatadogTools::class.java)

    /**
     * Search for error logs in Datadog.
     */
    @LlmTool(
        name = "datadog_search_logs",
        description = """Search Datadog logs for errors and patterns.
            Use this tool to find error messages, exceptions, and log patterns
            during an incident. Returns log entries with timestamps, messages,
            and attributes like error type and stack traces."""
    )
    fun searchLogs(
        @Param(description = "Datadog log query (e.g., 'service:api @status:error')")
        query: String,
        @Param(description = "Service name to filter logs")
        service: String? = null,
        @Param(description = "Environment (e.g., prod, staging)")
        env: String? = null,
        @Param(description = "Minutes to look back from now", required = false)
        minutesBack: Int = 30,
        @Param(description = "Maximum number of logs to return", required = false)
        limit: Int = 100,
    ): LogSearchResult {
        logger.info("Searching logs: query='$query', service=$service, env=$env, minutes=$minutesBack")

        val fullQuery = buildQuery(query, service, env)
        val end = Instant.now()
        val start = end.minus(Duration.ofMinutes(minutesBack.toLong()))

        return try {
            val logs = datadogClient.searchLogs(fullQuery, start, end, limit)
            
            LogSearchResult(
                success = true,
                query = fullQuery,
                totalCount = logs.size,
                timeRange = "$start to $end",
                logs = logs.take(limit).map { log ->
                    LogSummary(
                        timestamp = log.timestamp.toString(),
                        service = log.service,
                        status = log.status,
                        message = log.message.take(500),
                        errorType = log.errorType(),
                        errorMessage = log.errorMessage()?.take(200),
                    )
                },
                topPatterns = extractTopPatterns(logs),
            )
        } catch (e: Exception) {
            logger.error("Log search failed: ${e.message}", e)
            LogSearchResult(
                success = false,
                query = fullQuery,
                error = e.message,
            )
        }
    }

    /**
     * Query Datadog metrics for a time series.
     */
    @LlmTool(
        name = "datadog_query_metrics",
        description = """Query Datadog metrics to analyze performance data.
            Use this tool to check latency, error rates, throughput, and other
            metrics during an incident. Returns time series data with statistics."""
    )
    fun queryMetrics(
        @Param(description = "Datadog metric query (e.g., 'avg:trace.request.duration{service:api}')")
        query: String,
        @Param(description = "Minutes to look back from now", required = false)
        minutesBack: Int = 60,
    ): MetricQueryResult {
        logger.info("Querying metrics: query='$query', minutes=$minutesBack")

        val end = Instant.now()
        val start = end.minus(Duration.ofMinutes(minutesBack.toLong()))

        return try {
            val response = datadogClient.queryMetrics(query, start, end)
            val summary = response.summarize()

            MetricQueryResult(
                success = true,
                query = query,
                timeRange = "$start to $end",
                pointCount = summary.pointCount,
                average = summary.value,
                min = summary.min,
                max = summary.max,
                peakValue = summary.peakValue,
                peakTime = summary.peakTs?.let { Instant.ofEpochSecond(it).toString() },
            )
        } catch (e: Exception) {
            logger.error("Metric query failed: ${e.message}", e)
            MetricQueryResult(
                success = false,
                query = query,
                error = e.message,
            )
        }
    }

    /**
     * Search Datadog APM traces/spans.
     */
    @LlmTool(
        name = "datadog_search_traces",
        description = """Search Datadog APM traces to analyze request flow and dependencies.
            Use this tool to find slow requests, errors in specific endpoints,
            and downstream service issues. Returns span data with latency and error info."""
    )
    fun searchTraces(
        @Param(description = "Service name to search traces for")
        service: String,
        @Param(description = "Environment (e.g., prod, staging)")
        env: String,
        @Param(description = "Optional resource/endpoint filter (e.g., 'GET /api/orders')", required = false)
        resource: String? = null,
        @Param(description = "Only return error traces", required = false)
        errorsOnly: Boolean = false,
        @Param(description = "Minutes to look back from now", required = false)
        minutesBack: Int = 30,
        @Param(description = "Maximum number of traces to return", required = false)
        limit: Int = 100,
    ): TraceSearchResult {
        logger.info("Searching traces: service=$service, env=$env, resource=$resource, errors=$errorsOnly")

        val queryParts = mutableListOf("service:$service", "env:$env")
        resource?.let { queryParts.add("resource:\"$it\"") }
        if (errorsOnly) queryParts.add("error:1")
        val query = queryParts.joinToString(" ")

        val end = Instant.now()
        val start = end.minus(Duration.ofMinutes(minutesBack.toLong()))

        return try {
            val spans = datadogClient.searchSpans(query, start, end, limit)

            // Group by resource for summary
            val byResource = spans.groupBy { it.resource }
            val resourceStats = byResource.map { (res, resSpans) ->
                val stats = resSpans.stats()
                ResourceStats(
                    resource = res,
                    count = stats.count,
                    avgLatencyMs = stats.avgDurationMs,
                    p95LatencyMs = stats.p95Ms,
                    errorRate = stats.errorRate,
                )
            }.sortedByDescending { it.count }.take(10)

            // Group by dependency for downstream analysis
            val clientSpans = spans.filter { it.isClient }
            val byDependency = clientSpans.groupBy { it.dependencyName() ?: it.name ?: "unknown" }
            val dependencyStats = byDependency.map { (dep, depSpans) ->
                val stats = depSpans.stats()
                DependencyStats(
                    dependency = dep,
                    count = stats.count,
                    totalLatencyMs = stats.totalDurationMs,
                    avgLatencyMs = stats.avgDurationMs,
                    errorRate = stats.errorRate,
                )
            }.sortedByDescending { it.totalLatencyMs }.take(10)

            TraceSearchResult(
                success = true,
                query = query,
                timeRange = "$start to $end",
                totalSpans = spans.size,
                errorSpans = spans.count { it.isError },
                topEndpoints = resourceStats,
                topDependencies = dependencyStats,
            )
        } catch (e: Exception) {
            logger.error("Trace search failed: ${e.message}", e)
            TraceSearchResult(
                success = false,
                query = query,
                error = e.message,
            )
        }
    }

    /**
     * Get recent events from Datadog (deploys, alerts, etc).
     */
    @LlmTool(
        name = "datadog_get_events",
        description = """Get recent events from Datadog including deployments, alerts, and configuration changes.
            Use this tool to correlate incidents with recent changes. Returns events
            with timestamps, titles, and tags."""
    )
    fun getEvents(
        @Param(description = "Service name to filter events", required = false)
        service: String? = null,
        @Param(description = "Environment to filter events", required = false)
        env: String? = null,
        @Param(description = "Event type filter (deploy, alert, config)", required = false)
        eventType: String? = null,
        @Param(description = "Minutes to look back from now", required = false)
        minutesBack: Int = 60,
    ): EventSearchResult {
        logger.info("Getting events: service=$service, env=$env, type=$eventType, minutes=$minutesBack")

        val tags = buildList {
            service?.let { add("service:$it") }
            env?.let { add("env:$it") }
        }.joinToString(",").ifEmpty { null }

        val end = Instant.now()
        val start = end.minus(Duration.ofMinutes(minutesBack.toLong()))

        return try {
            val response = datadogClient.searchEvents(start, end, tags)
            
            val filteredEvents = response.events.filter { event ->
                when (eventType?.lowercase()) {
                    "deploy" -> event.isDeployment()
                    "config" -> event.isConfigChange()
                    "scale", "autoscale" -> event.isAutoscaling()
                    else -> true
                }
            }

            EventSearchResult(
                success = true,
                timeRange = "$start to $end",
                totalEvents = filteredEvents.size,
                events = filteredEvents.take(20).map { event ->
                    EventSummary(
                        timestamp = event.dateHappened.toString(),
                        title = event.title,
                        text = event.text.take(300),
                        source = event.source,
                        tags = event.tags,
                        isDeployment = event.isDeployment(),
                        isConfigChange = event.isConfigChange(),
                    )
                },
                deployments = filteredEvents.count { it.isDeployment() },
                configChanges = filteredEvents.count { it.isConfigChange() },
            )
        } catch (e: Exception) {
            logger.error("Event search failed: ${e.message}", e)
            EventSearchResult(
                success = false,
                error = e.message,
            )
        }
    }

    /**
     * Get monitor details and status.
     */
    @LlmTool(
        name = "datadog_get_monitor",
        description = """Get details about a Datadog monitor including its query, thresholds, and current status.
            Use this tool when investigating an alert to understand what triggered it."""
    )
    fun getMonitor(
        @Param(description = "Datadog monitor ID")
        monitorId: Long,
    ): MonitorResult {
        logger.info("Getting monitor: $monitorId")

        return try {
            val monitor = datadogClient.getMonitor(monitorId)

            MonitorResult(
                success = true,
                id = monitor.id,
                name = monitor.name,
                type = monitor.type,
                query = monitor.query,
                message = monitor.message,
                tags = monitor.tags,
                overallState = monitor.state?.overallState,
            )
        } catch (e: Exception) {
            logger.error("Get monitor failed: ${e.message}", e)
            MonitorResult(
                success = false,
                id = monitorId,
                error = e.message,
            )
        }
    }

    /**
     * Compare metrics between two time periods (incident vs baseline).
     */
    @LlmTool(
        name = "datadog_compare_periods",
        description = """Compare metrics between incident and baseline periods to identify anomalies.
            Use this tool to quantify the impact of an incident by comparing
            current metrics against a healthy baseline."""
    )
    fun comparePeriods(
        @Param(description = "Datadog metric query")
        query: String,
        @Param(description = "Duration of incident window in minutes")
        incidentMinutes: Int = 30,
        @Param(description = "Duration of baseline window in minutes (before incident)")
        baselineMinutes: Int = 30,
    ): ComparisonResult {
        logger.info("Comparing periods: query='$query', incident=${incidentMinutes}m, baseline=${baselineMinutes}m")

        val now = Instant.now()
        val incidentEnd = now
        val incidentStart = now.minus(Duration.ofMinutes(incidentMinutes.toLong()))
        val baselineEnd = incidentStart
        val baselineStart = baselineEnd.minus(Duration.ofMinutes(baselineMinutes.toLong()))

        return try {
            val incidentResponse = datadogClient.queryMetrics(query, incidentStart, incidentEnd)
            val baselineResponse = datadogClient.queryMetrics(query, baselineStart, baselineEnd)

            val incidentSummary = incidentResponse.summarize()
            val baselineSummary = baselineResponse.summarize()

            val percentChange = percentChange(baselineSummary.value, incidentSummary.value)

            ComparisonResult(
                success = true,
                query = query,
                baseline = PeriodSummary(
                    timeRange = "$baselineStart to $baselineEnd",
                    average = baselineSummary.value,
                    min = baselineSummary.min,
                    max = baselineSummary.max,
                    pointCount = baselineSummary.pointCount,
                ),
                incident = PeriodSummary(
                    timeRange = "$incidentStart to $incidentEnd",
                    average = incidentSummary.value,
                    min = incidentSummary.min,
                    max = incidentSummary.max,
                    pointCount = incidentSummary.pointCount,
                ),
                percentChange = percentChange,
                assessment = when {
                    percentChange == null -> "Unable to calculate change"
                    percentChange > 100 -> "CRITICAL: ${percentChange.toInt()}% increase"
                    percentChange > 50 -> "HIGH: ${percentChange.toInt()}% increase"
                    percentChange > 20 -> "MEDIUM: ${percentChange.toInt()}% increase"
                    percentChange < -20 -> "IMPROVED: ${(-percentChange).toInt()}% decrease"
                    else -> "NORMAL: Within expected range"
                },
            )
        } catch (e: Exception) {
            logger.error("Comparison failed: ${e.message}", e)
            ComparisonResult(
                success = false,
                query = query,
                error = e.message,
            )
        }
    }

    // Helper functions

    private fun buildQuery(baseQuery: String, service: String?, env: String?): String {
        val parts = mutableListOf(baseQuery)
        service?.let { parts.add("service:$it") }
        env?.let { parts.add("env:$it") }
        return parts.joinToString(" ")
    }

    private fun extractTopPatterns(logs: List<LogEntry>): List<PatternSummary> {
        return logs
            .groupBy { it.fingerprint() }
            .map { (fingerprint, groupLogs) ->
                PatternSummary(
                    pattern = LogEntry.normalizeMessage(groupLogs.first().message).take(100),
                    count = groupLogs.size,
                    errorType = groupLogs.first().errorType(),
                    firstSeen = groupLogs.minOf { it.timestamp }.toString(),
                )
            }
            .sortedByDescending { it.count }
            .take(10)
    }
}

// Result data classes for tool responses

data class LogSearchResult(
    val success: Boolean,
    val query: String? = null,
    val totalCount: Int = 0,
    val timeRange: String? = null,
    val logs: List<LogSummary> = emptyList(),
    val topPatterns: List<PatternSummary> = emptyList(),
    val error: String? = null,
)

data class LogSummary(
    val timestamp: String,
    val service: String?,
    val status: String?,
    val message: String,
    val errorType: String?,
    val errorMessage: String?,
)

data class PatternSummary(
    val pattern: String,
    val count: Int,
    val errorType: String?,
    val firstSeen: String,
)

data class MetricQueryResult(
    val success: Boolean,
    val query: String,
    val timeRange: String? = null,
    val pointCount: Int = 0,
    val average: Double? = null,
    val min: Double? = null,
    val max: Double? = null,
    val peakValue: Double? = null,
    val peakTime: String? = null,
    val error: String? = null,
)

data class TraceSearchResult(
    val success: Boolean,
    val query: String? = null,
    val timeRange: String? = null,
    val totalSpans: Int = 0,
    val errorSpans: Int = 0,
    val topEndpoints: List<ResourceStats> = emptyList(),
    val topDependencies: List<DependencyStats> = emptyList(),
    val error: String? = null,
)

data class ResourceStats(
    val resource: String,
    val count: Int,
    val avgLatencyMs: Double,
    val p95LatencyMs: Double,
    val errorRate: Double,
)

data class DependencyStats(
    val dependency: String,
    val count: Int,
    val totalLatencyMs: Double,
    val avgLatencyMs: Double,
    val errorRate: Double,
)

data class EventSearchResult(
    val success: Boolean,
    val timeRange: String? = null,
    val totalEvents: Int = 0,
    val events: List<EventSummary> = emptyList(),
    val deployments: Int = 0,
    val configChanges: Int = 0,
    val error: String? = null,
)

data class EventSummary(
    val timestamp: String,
    val title: String,
    val text: String,
    val source: String?,
    val tags: List<String>,
    val isDeployment: Boolean,
    val isConfigChange: Boolean,
)

data class MonitorResult(
    val success: Boolean,
    val id: Long,
    val name: String? = null,
    val type: String? = null,
    val query: String? = null,
    val message: String? = null,
    val tags: List<String> = emptyList(),
    val overallState: String? = null,
    val error: String? = null,
)

data class ComparisonResult(
    val success: Boolean,
    val query: String,
    val baseline: PeriodSummary? = null,
    val incident: PeriodSummary? = null,
    val percentChange: Double? = null,
    val assessment: String? = null,
    val error: String? = null,
)

data class PeriodSummary(
    val timeRange: String,
    val average: Double?,
    val min: Double?,
    val max: Double?,
    val pointCount: Int,
)
