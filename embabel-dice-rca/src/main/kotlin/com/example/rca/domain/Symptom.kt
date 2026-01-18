package com.example.rca.domain

import java.time.Instant

/**
 * Represents a detected symptom/anomaly during an incident.
 * Maps to the Python dd_rca Symptom model.
 */
data class Symptom(
    val type: SymptomType,
    val queryOrSignature: String,
    val baselineValue: Double? = null,
    val incidentValue: Double? = null,
    val percentChange: Double? = null,
    val peakTs: Instant? = null,
    val peakValue: Double? = null
)

enum class SymptomType {
    LATENCY,
    ERROR_RATE,
    LOG_SIGNATURE,
    METRIC,
    MEMORY,
    CPU,
    THROUGHPUT
}

/**
 * Extension function to calculate severity based on percent change.
 */
fun Symptom.severity(): Severity = when {
    percentChange == null -> Severity.UNKNOWN
    percentChange >= 100.0 -> Severity.CRITICAL
    percentChange >= 50.0 -> Severity.HIGH
    percentChange >= 20.0 -> Severity.MEDIUM
    percentChange >= 5.0 -> Severity.LOW
    else -> Severity.NORMAL
}

enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    NORMAL,
    UNKNOWN
}
