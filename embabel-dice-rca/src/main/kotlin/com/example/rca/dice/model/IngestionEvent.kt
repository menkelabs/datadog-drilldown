package com.example.rca.dice.model

import java.time.Instant

/**
 * Base interface for all Dice ingestion events.
 */
sealed interface IngestionEvent {
    val id: String
    val timestamp: Instant
    val source: String
}

/**
 * Alert trigger event from monitoring systems.
 */
data class AlertTrigger(
    override val id: String,
    override val timestamp: Instant,
    override val source: String = "datadog",
    val alertType: AlertType,
    val monitorId: Long? = null,
    val query: String? = null,
    val service: String? = null,
    val env: String? = null,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val message: String? = null,
    val tags: List<String> = emptyList()
) : IngestionEvent

enum class AlertType {
    MONITOR_ALERT,
    ANOMALY_DETECTION,
    LOG_THRESHOLD,
    METRIC_THRESHOLD,
    APM_ALERT,
    MANUAL
}

enum class AlertSeverity {
    CRITICAL,
    HIGH,
    WARNING,
    LOW,
    INFO
}

/**
 * Metric anomaly event from anomaly detection.
 */
data class MetricAnomaly(
    override val id: String,
    override val timestamp: Instant,
    override val source: String = "datadog",
    val metricName: String,
    val query: String,
    val expectedValue: Double,
    val actualValue: Double,
    val deviationPercent: Double,
    val service: String? = null,
    val env: String? = null
) : IngestionEvent {
    val isSignificant: Boolean
        get() = kotlin.math.abs(deviationPercent) > 20.0
}

/**
 * Log stream event containing batched log entries.
 */
data class LogStreamEvent(
    override val id: String,
    override val timestamp: Instant,
    override val source: String = "datadog",
    val query: String,
    val logs: List<LogData>,
    val service: String? = null,
    val env: String? = null
) : IngestionEvent

/**
 * Simplified log data for ingestion.
 */
data class LogData(
    val timestamp: Instant,
    val message: String,
    val level: String? = null,
    val service: String? = null,
    val host: String? = null,
    val attributes: Map<String, Any> = emptyMap()
)

/**
 * Manual incident report from user.
 */
data class ManualIncidentReport(
    override val id: String,
    override val timestamp: Instant,
    override val source: String = "user",
    val title: String,
    val description: String,
    val service: String? = null,
    val env: String? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val tags: List<String> = emptyList()
) : IngestionEvent

/**
 * Result of an ingestion operation.
 */
data class IngestionResult(
    val eventId: String,
    val success: Boolean,
    val incidentId: String? = null,
    val message: String? = null,
    val analysisTriggered: Boolean = false
)
