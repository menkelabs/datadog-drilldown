package com.example.rca.datadog.mock

import com.example.rca.datadog.dto.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Pre-built Datadog mock scenarios for tests and `mock-datadog-scenarios` profile.
 */
object TestScenarios {

    private val baseTime = Instant.parse("2026-01-15T12:00:00Z")

    val databaseLatencyScenario: DatadogScenario = scenario("database-latency") {
        description("High latency caused by database connection pool exhaustion")
        incidentStart(baseTime)

        monitor(MonitorResponse(
            id = 12345,
            name = "API P95 Latency",
            type = "metric alert",
            query = "p95:trace.api.request.duration{service:api,env:prod}",
            tags = listOf("service:api", "env:prod")
        ))

        baselineMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "trace.api.request.duration",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.minus(60 - i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 50.0 + (i % 5) * 2
                    )
                }
            ))
        ))

        incidentMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "trace.api.request.duration",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.plus(i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 450.0 + (i % 5) * 50
                    )
                }
            ))
        ))

        baselineLogs(listOf(
            createLogEntry(baseTime.minus(45, ChronoUnit.MINUTES), "Request completed", "info"),
            createLogEntry(baseTime.minus(40, ChronoUnit.MINUTES), "Request completed", "info")
        ))

        incidentLogs(listOf(
            createErrorLogEntry(baseTime.plus(1, ChronoUnit.MINUTES),
                "TimeoutError: Connection pool exhausted after 5000ms", "TimeoutError"),
            createErrorLogEntry(baseTime.plus(2, ChronoUnit.MINUTES),
                "TimeoutError: Connection pool exhausted after 5000ms", "TimeoutError"),
            createErrorLogEntry(baseTime.plus(3, ChronoUnit.MINUTES),
                "TimeoutError: Connection pool exhausted after 5000ms", "TimeoutError"),
            createErrorLogEntry(baseTime.plus(5, ChronoUnit.MINUTES),
                "SQLException: Unable to acquire connection from pool", "SQLException"),
            createErrorLogEntry(baseTime.plus(10, ChronoUnit.MINUTES),
                "TimeoutError: Connection pool exhausted after 5000ms", "TimeoutError")
        ))

        baselineSpans(listOf(
            createServerSpan(baseTime.minus(45, ChronoUnit.MINUTES), "GET /users", 50_000_000),
            createClientSpan(baseTime.minus(45, ChronoUnit.MINUTES), "postgres.query", "postgres", 20_000_000)
        ))

        incidentSpans(listOf(
            createServerSpan(baseTime.plus(2, ChronoUnit.MINUTES), "GET /users", 500_000_000),
            createClientSpan(baseTime.plus(2, ChronoUnit.MINUTES), "postgres.query", "postgres", 450_000_000, isError = true),
            createServerSpan(baseTime.plus(5, ChronoUnit.MINUTES), "GET /users", 520_000_000),
            createClientSpan(baseTime.plus(5, ChronoUnit.MINUTES), "postgres.query", "postgres", 470_000_000, isError = true)
        ))

        events(listOf(
            EventEntry(
                id = 1001,
                title = "Deployment: api-service v2.1.0",
                text = "Deployed new version with connection pool changes",
                dateHappened = baseTime.minus(5, ChronoUnit.MINUTES),
                source = "deploy",
                tags = listOf("service:api", "env:prod", "deploy")
            )
        ))
    }

    val downstreamFailureScenario: DatadogScenario = scenario("downstream-failure") {
        description("Error rate spike caused by downstream payment service failure")
        incidentStart(baseTime)

        monitor(MonitorResponse(
            id = 12346,
            name = "API Error Rate",
            type = "metric alert",
            query = "sum:trace.api.request.errors{service:api,env:prod}.as_count()",
            tags = listOf("service:api", "env:prod")
        ))

        baselineMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "trace.api.request.errors",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.minus(60 - i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 2.0 + (i % 3)
                    )
                }
            ))
        ))

        incidentMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "trace.api.request.errors",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.plus(i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 150.0 + (i % 5) * 20
                    )
                }
            ))
        ))

        baselineLogs(listOf(
            createLogEntry(baseTime.minus(45, ChronoUnit.MINUTES), "Payment processed successfully", "info")
        ))

        incidentLogs(listOf(
            createErrorLogEntry(baseTime.plus(1, ChronoUnit.MINUTES),
                "ConnectionError: Failed to connect to payment-service:8080", "ConnectionError"),
            createErrorLogEntry(baseTime.plus(2, ChronoUnit.MINUTES),
                "ConnectionError: Failed to connect to payment-service:8080", "ConnectionError"),
            createErrorLogEntry(baseTime.plus(3, ChronoUnit.MINUTES),
                "HTTP 503: payment-service unavailable", "HttpError"),
            createErrorLogEntry(baseTime.plus(4, ChronoUnit.MINUTES),
                "ConnectionError: Failed to connect to payment-service:8080", "ConnectionError"),
            createErrorLogEntry(baseTime.plus(5, ChronoUnit.MINUTES),
                "CircuitBreaker OPEN for payment-service", "CircuitBreakerOpen")
        ))

        baselineSpans(listOf(
            createServerSpan(baseTime.minus(45, ChronoUnit.MINUTES), "POST /checkout", 100_000_000),
            createClientSpan(baseTime.minus(45, ChronoUnit.MINUTES), "http.request", "payment-service", 50_000_000)
        ))

        incidentSpans(listOf(
            createServerSpan(baseTime.plus(2, ChronoUnit.MINUTES), "POST /checkout", 5000_000_000, isError = true),
            createClientSpan(baseTime.plus(2, ChronoUnit.MINUTES), "http.request", "payment-service", 5000_000_000, isError = true),
            createServerSpan(baseTime.plus(4, ChronoUnit.MINUTES), "POST /checkout", 100_000_000, isError = true),
            createClientSpan(baseTime.plus(4, ChronoUnit.MINUTES), "http.request", "payment-service", 50_000_000, isError = true)
        ))
    }

    val memoryPressureScenario: DatadogScenario = scenario("memory-pressure") {
        description("Memory pressure causing OOM errors and degraded performance")
        incidentStart(baseTime)

        monitor(MonitorResponse(
            id = 12347,
            name = "JVM Heap Usage",
            type = "metric alert",
            query = "avg:jvm.heap.used{service:api,env:prod}",
            tags = listOf("service:api", "env:prod")
        ))

        baselineMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "jvm.heap.used",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.minus(60 - i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 0.6 + (i % 5) * 0.02
                    )
                }
            ))
        ))

        incidentMetrics(MetricResponse(
            series = listOf(MetricSeries(
                metric = "jvm.heap.used",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.plus(i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 0.92 + (i % 5) * 0.01
                    )
                }
            ))
        ))

        baselineLogs(listOf(
            createLogEntry(baseTime.minus(45, ChronoUnit.MINUTES), "GC completed: 15ms pause", "info")
        ))

        incidentLogs(listOf(
            createErrorLogEntry(baseTime.plus(1, ChronoUnit.MINUTES),
                "GC overhead limit exceeded - long pause 5000ms", "GCOverhead"),
            createErrorLogEntry(baseTime.plus(3, ChronoUnit.MINUTES),
                "OutOfMemoryError: Java heap space", "OutOfMemoryError"),
            createErrorLogEntry(baseTime.plus(5, ChronoUnit.MINUTES),
                "OutOfMemoryError: Java heap space", "OutOfMemoryError"),
            createErrorLogEntry(baseTime.plus(7, ChronoUnit.MINUTES),
                "GC overhead limit exceeded - long pause 8000ms", "GCOverhead")
        ))

        apmError("APM data unavailable during high memory pressure")
    }

    val healthyScenario: DatadogScenario = scenario("healthy") {
        description("Healthy system with no significant issues")
        incidentStart(baseTime)

        monitor(MonitorResponse(
            id = 12348,
            name = "API Health Check",
            type = "metric alert",
            query = "avg:system.load.1{service:api,env:prod}",
            tags = listOf("service:api", "env:prod")
        ))

        val healthyMetrics = MetricResponse(
            series = listOf(MetricSeries(
                metric = "system.load.1",
                pointlist = (1..10).map { i ->
                    MetricPoint(
                        timestamp = baseTime.minus(60 - i.toLong(), ChronoUnit.MINUTES).toEpochMilli(),
                        value = 1.0 + (i % 3) * 0.1
                    )
                }
            ))
        )

        baselineMetrics(healthyMetrics)
        incidentMetrics(healthyMetrics)

        val healthyLogs = listOf(
            createLogEntry(baseTime.minus(30, ChronoUnit.MINUTES), "Health check passed", "info"),
            createLogEntry(baseTime.plus(5, ChronoUnit.MINUTES), "Health check passed", "info")
        )

        baselineLogs(healthyLogs)
        incidentLogs(healthyLogs)

        val healthySpans = listOf(
            createServerSpan(baseTime, "GET /health", 5_000_000),
            createClientSpan(baseTime, "redis.ping", "redis", 1_000_000)
        )

        baselineSpans(healthySpans)
        incidentSpans(healthySpans)
    }

    private fun createLogEntry(
        timestamp: Instant,
        message: String,
        level: String,
        service: String = "api",
        host: String = "api-host-1"
    ): LogEntry = LogEntry(
        id = "log-${timestamp.toEpochMilli()}",
        timestamp = timestamp,
        service = service,
        host = host,
        status = level,
        message = message,
        attributes = mapOf(
            "service" to service,
            "host" to host,
            "level" to level
        ),
        tags = listOf("service:$service", "env:prod")
    )

    private fun createErrorLogEntry(
        timestamp: Instant,
        message: String,
        errorType: String,
        service: String = "api"
    ): LogEntry = LogEntry(
        id = "log-${timestamp.toEpochMilli()}",
        timestamp = timestamp,
        service = service,
        host = "api-host-1",
        status = "error",
        message = message,
        attributes = mapOf(
            "service" to service,
            "error.type" to errorType,
            "error.message" to message,
            "@status" to "error"
        ),
        tags = listOf("service:$service", "env:prod")
    )

    private fun createServerSpan(
        timestamp: Instant,
        resource: String,
        duration: Long,
        isError: Boolean = false,
        service: String = "api"
    ): SpanEntry = SpanEntry(
        traceId = "trace-${timestamp.toEpochMilli()}",
        spanId = "span-server-${timestamp.toEpochMilli()}",
        service = service,
        resource = resource,
        name = resource.split(" ").last(),
        timestamp = timestamp,
        duration = duration,
        spanKind = SpanKind.SERVER,
        isError = isError
    )

    private fun createClientSpan(
        timestamp: Instant,
        name: String,
        peerService: String,
        duration: Long,
        isError: Boolean = false,
        service: String = "api"
    ): SpanEntry = SpanEntry(
        traceId = "trace-${timestamp.toEpochMilli()}",
        spanId = "span-client-${timestamp.toEpochMilli()}",
        service = service,
        resource = name,
        name = name,
        timestamp = timestamp,
        duration = duration,
        spanKind = SpanKind.CLIENT,
        isError = isError,
        peerService = peerService
    )
}
