package com.example.rca.config

import com.example.rca.datadog.DatadogClient
import com.example.rca.datadog.dto.*
import com.example.rca.datadog.impl.RealHttpDatadogClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Instant

/**
 * Configuration for Datadog client.
 * Swaps between Real and Mock implementations based on Spring Profiles.
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
    @Profile("test", "mock-datadog")
    fun testDatadogClient(): DatadogClient {
        // This will be overridden by MockDatadogClient in tests
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
