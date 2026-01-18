package com.example.rca.mock

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.DatadogException
import com.example.rca.datadog.dto.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Mock Datadog client for integration testing.
 * 
 * Supports loading different scenarios to simulate various incident types.
 */
class MockDatadogClient : DatadogClient {

    private val scenarios = mutableMapOf<String, DatadogScenario>()
    private var activeScenarioName: String? = null

    /**
     * Load a scenario for testing.
     */
    fun loadScenario(name: String, scenario: DatadogScenario) {
        scenarios[name] = scenario
    }

    /**
     * Set the active scenario.
     */
    fun setActiveScenario(name: String) {
        require(name in scenarios) { "Scenario '$name' not loaded" }
        activeScenarioName = name
    }

    /**
     * Get the currently active scenario.
     */
    private val activeScenario: DatadogScenario
        get() = scenarios[activeScenarioName]
            ?: throw IllegalStateException("No active scenario set")

    /**
     * Clear all scenarios.
     */
    fun clearScenarios() {
        scenarios.clear()
        activeScenarioName = null
    }

    // DatadogClient implementation

    override fun getMonitor(monitorId: Long): MonitorResponse {
        return activeScenario.monitor
    }

    override fun queryMetrics(query: String, start: Instant, end: Instant): MetricResponse {
        // Return baseline or incident metrics based on time range
        val isBaseline = end < activeScenario.incidentStart
        return if (isBaseline) {
            activeScenario.baselineMetrics
        } else {
            activeScenario.incidentMetrics
        }
    }

    override fun searchLogs(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int,
        maxPages: Int
    ): List<LogEntry> {
        val isBaseline = end < activeScenario.incidentStart
        return if (isBaseline) {
            activeScenario.baselineLogs
        } else {
            activeScenario.incidentLogs
        }
    }

    override fun searchSpans(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int,
        maxPages: Int
    ): List<SpanEntry> {
        if (activeScenario.apmError != null) {
            throw DatadogException(activeScenario.apmError!!)
        }
        
        val isBaseline = end < activeScenario.incidentStart
        return if (isBaseline) {
            activeScenario.baselineSpans
        } else {
            activeScenario.incidentSpans
        }
    }

    override fun searchEvents(start: Instant, end: Instant, tags: String?): EventResponse {
        return EventResponse(events = activeScenario.events)
    }
}

/**
 * A scenario containing mock data for a specific incident type.
 */
data class DatadogScenario(
    val name: String,
    val description: String,
    val incidentStart: Instant,
    val monitor: MonitorResponse,
    val baselineMetrics: MetricResponse,
    val incidentMetrics: MetricResponse,
    val baselineLogs: List<LogEntry>,
    val incidentLogs: List<LogEntry>,
    val baselineSpans: List<SpanEntry>,
    val incidentSpans: List<SpanEntry>,
    val events: List<EventEntry> = emptyList(),
    val apmError: String? = null
)

/**
 * Builder for creating test scenarios.
 */
class ScenarioBuilder(private val name: String) {
    private var description: String = ""
    private var incidentStart: Instant = Instant.now().minus(30, ChronoUnit.MINUTES)
    private var monitor: MonitorResponse = defaultMonitor()
    private var baselineMetrics: MetricResponse = MetricResponse()
    private var incidentMetrics: MetricResponse = MetricResponse()
    private var baselineLogs: MutableList<LogEntry> = mutableListOf()
    private var incidentLogs: MutableList<LogEntry> = mutableListOf()
    private var baselineSpans: MutableList<SpanEntry> = mutableListOf()
    private var incidentSpans: MutableList<SpanEntry> = mutableListOf()
    private var events: MutableList<EventEntry> = mutableListOf()
    private var apmError: String? = null

    fun description(desc: String) = apply { this.description = desc }
    
    fun incidentStart(start: Instant) = apply { this.incidentStart = start }
    
    fun monitor(monitor: MonitorResponse) = apply { this.monitor = monitor }
    
    fun baselineMetrics(metrics: MetricResponse) = apply { this.baselineMetrics = metrics }
    
    fun incidentMetrics(metrics: MetricResponse) = apply { this.incidentMetrics = metrics }
    
    fun baselineLogs(logs: List<LogEntry>) = apply { this.baselineLogs = logs.toMutableList() }
    
    fun incidentLogs(logs: List<LogEntry>) = apply { this.incidentLogs = logs.toMutableList() }
    
    fun addBaselineLog(log: LogEntry) = apply { this.baselineLogs.add(log) }
    
    fun addIncidentLog(log: LogEntry) = apply { this.incidentLogs.add(log) }
    
    fun baselineSpans(spans: List<SpanEntry>) = apply { this.baselineSpans = spans.toMutableList() }
    
    fun incidentSpans(spans: List<SpanEntry>) = apply { this.incidentSpans = spans.toMutableList() }
    
    fun addBaselineSpan(span: SpanEntry) = apply { this.baselineSpans.add(span) }
    
    fun addIncidentSpan(span: SpanEntry) = apply { this.incidentSpans.add(span) }
    
    fun events(events: List<EventEntry>) = apply { this.events = events.toMutableList() }
    
    fun addEvent(event: EventEntry) = apply { this.events.add(event) }
    
    fun apmError(error: String?) = apply { this.apmError = error }

    fun build(): DatadogScenario = DatadogScenario(
        name = name,
        description = description,
        incidentStart = incidentStart,
        monitor = monitor,
        baselineMetrics = baselineMetrics,
        incidentMetrics = incidentMetrics,
        baselineLogs = baselineLogs,
        incidentLogs = incidentLogs,
        baselineSpans = baselineSpans,
        incidentSpans = incidentSpans,
        events = events,
        apmError = apmError
    )

    companion object {
        private fun defaultMonitor() = MonitorResponse(
            id = 12345,
            name = "Test Monitor",
            type = "metric alert",
            query = "avg:system.load.1{service:api,env:prod} > 10",
            tags = listOf("service:api", "env:prod")
        )
    }
}

fun scenario(name: String, init: ScenarioBuilder.() -> Unit): DatadogScenario {
    return ScenarioBuilder(name).apply(init).build()
}
