package com.example.rca.config

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.dto.*
import com.example.rca.datadog.impl.RealHttpDatadogClient
import com.example.rca.datadog.mock.MockDatadogClient
import com.example.rca.datadog.mock.TestScenarios
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Instant

/**
 * Configuration for Datadog client.
 *
 * - **default** + `datadog.api-key` → [RealHttpDatadogClient]
 * - **mock-datadog** → [NoOpDatadogClient] (empty telemetry; do not combine with **mock-datadog-scenarios**)
 * - **test** or **mock-datadog-scenarios** → [MockDatadogClient] with [TestScenarios] fixtures
 */
@Configuration
class DatadogConfig {

    @Bean
    @Profile("prod", "default")
    @ConditionalOnProperty(prefix = "datadog", name = ["api-key"])
    fun datadogClient(
        @Value("\${datadog.api-key}") apiKey: String,
        @Value("\${datadog.app-key}") appKey: String,
        @Value("\${datadog.site:datadoghq.com}") site: String
    ): DatadogClient {
        return RealHttpDatadogClient(apiKey, appKey, site)
    }

    @Bean
    @Primary
    @Profile("test", "mock-datadog-scenarios")
    fun scenarioMockDatadogClient(
        @Value("\${datadog.mock.active-scenario:database-latency}") activeScenario: String,
    ): MockDatadogClient {
        val allowed = setOf(
            "database-latency",
            "downstream-failure",
            "memory-pressure",
            "healthy",
        )
        require(activeScenario in allowed) {
            "datadog.mock.active-scenario must be one of $allowed, got: $activeScenario"
        }
        val c = MockDatadogClient()
        c.loadScenario("database-latency", TestScenarios.databaseLatencyScenario)
        c.loadScenario("downstream-failure", TestScenarios.downstreamFailureScenario)
        c.loadScenario("memory-pressure", TestScenarios.memoryPressureScenario)
        c.loadScenario("healthy", TestScenarios.healthyScenario)
        c.setActiveScenario(activeScenario)
        return c
    }

    @Bean
    @Primary
    @Profile("mock-datadog")
    fun noOpDatadogClient(): DatadogClient {
        return NoOpDatadogClient()
    }
}

/**
 * No-op Datadog client for testing without configuration.
 */
class NoOpDatadogClient : DatadogClient {
    override fun getMonitor(monitorId: Long) = MonitorResponse(
        id = monitorId,
        name = "Test Monitor",
        type = "metric alert",
        query = "avg:system.load.1{*}",
        tags = emptyList()
    )

    override fun queryMetrics(query: String, start: Instant, end: Instant) = MetricResponse()

    override fun searchLogs(query: String, start: Instant, end: Instant, limit: Int, maxPages: Int) = emptyList<LogEntry>()

    override fun searchSpans(query: String, start: Instant, end: Instant, limit: Int, maxPages: Int) = emptyList<SpanEntry>()

    override fun searchEvents(start: Instant, end: Instant, tags: String?) = EventResponse()
}
