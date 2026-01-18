package com.example.rca.datadog

import com.example.rca.datadog.dto.*
import java.time.Instant

/**
 * Interface for Datadog API operations.
 * Implementations can be real (HTTP) or mock (for testing).
 */
interface DatadogClient {
    
    /**
     * Get monitor details by ID.
     */
    fun getMonitor(monitorId: Long): MonitorResponse

    /**
     * Query metrics using Datadog query language.
     */
    fun queryMetrics(query: String, start: Instant, end: Instant): MetricResponse

    /**
     * Search logs with the given query and time range.
     */
    fun searchLogs(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int = 1000,
        maxPages: Int = 2
    ): List<LogEntry>

    /**
     * Search APM spans/traces with the given query and time range.
     */
    fun searchSpans(
        query: String,
        start: Instant,
        end: Instant,
        limit: Int = 1000,
        maxPages: Int = 2
    ): List<SpanEntry>

    /**
     * Search events (deploys, alerts, etc.) in the given time range.
     */
    fun searchEvents(
        start: Instant,
        end: Instant,
        tags: String? = null
    ): EventResponse
}

/**
 * Exception thrown by Datadog client operations.
 */
class DatadogException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)
