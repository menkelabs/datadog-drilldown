package com.example.rca.domain

import java.time.Instant

/**
 * Represents a cluster of similar log messages grouped by fingerprint.
 * Maps to the Python dd_rca LogCluster model.
 */
data class LogCluster(
    val fingerprint: String,
    val template: String,
    val countIncident: Int,
    val countBaseline: Int,
    val firstSeen: Instant? = null,
    val sample: Map<String, Any>? = null
) {
    /**
     * Calculate the growth ratio between incident and baseline.
     */
    val growthRatio: Double
        get() = if (countBaseline > 0) {
            countIncident.toDouble() / countBaseline.toDouble()
        } else if (countIncident > 0) {
            Double.POSITIVE_INFINITY
        } else {
            1.0
        }

    /**
     * Determine if this cluster represents a new pattern (not seen in baseline).
     */
    val isNewPattern: Boolean
        get() = countBaseline == 0 && countIncident > 0

    /**
     * Calculate a simple anomaly score based on growth and volume.
     */
    fun anomalyScore(): Double {
        val volumeScore = countIncident.toDouble().coerceAtMost(1000.0) / 1000.0
        val growthScore = when {
            isNewPattern -> 1.0
            growthRatio >= 10.0 -> 0.9
            growthRatio >= 5.0 -> 0.7
            growthRatio >= 2.0 -> 0.5
            growthRatio >= 1.5 -> 0.3
            else -> 0.1
        }
        return (volumeScore * 0.4 + growthScore * 0.6).coerceIn(0.0, 1.0)
    }
}

/**
 * Extension to extract error information from a log cluster.
 */
fun LogCluster.extractErrorType(): String? {
    val sample = this.sample ?: return null
    return sample["error.type"] as? String
        ?: sample["error_type"] as? String
        ?: extractFromTemplate()
}

private fun LogCluster.extractFromTemplate(): String? {
    val errorPatterns = listOf(
        "TimeoutError", "ConnectionError", "NullPointerException",
        "OutOfMemoryError", "IOException", "SQLException"
    )
    return errorPatterns.firstOrNull { template.contains(it, ignoreCase = true) }
}
