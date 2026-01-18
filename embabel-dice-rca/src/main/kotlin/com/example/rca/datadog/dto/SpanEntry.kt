package com.example.rca.datadog.dto

import java.time.Instant

/**
 * Represents an APM span/trace from Datadog.
 */
data class SpanEntry(
    val traceId: String,
    val spanId: String? = null,
    val service: String,
    val resource: String,
    val name: String? = null,
    val timestamp: Instant,
    val duration: Long,  // nanoseconds
    val spanKind: SpanKind = SpanKind.UNKNOWN,
    val isError: Boolean = false,
    val statusCode: Int? = null,
    val peerService: String? = null,
    val attributes: Map<String, Any> = emptyMap()
) {
    /**
     * Duration in milliseconds.
     */
    val durationMs: Double
        get() = duration / 1_000_000.0

    /**
     * Check if this is a server-side span.
     */
    val isServer: Boolean
        get() = spanKind == SpanKind.SERVER

    /**
     * Check if this is a client-side span (outgoing call).
     */
    val isClient: Boolean
        get() = spanKind == SpanKind.CLIENT

    /**
     * Get the dependency name for client spans.
     */
    fun dependencyName(): String? = when {
        !isClient -> null
        peerService != null -> peerService
        name != null -> name.split(".").firstOrNull()
        else -> null
    }
}

enum class SpanKind {
    SERVER,
    CLIENT,
    PRODUCER,
    CONSUMER,
    INTERNAL,
    UNKNOWN
}

/**
 * Statistics for a group of spans (e.g., by endpoint or dependency).
 */
data class SpanStats(
    val count: Int,
    val totalDurationMs: Double,
    val avgDurationMs: Double,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val errorCount: Int,
    val errorRate: Double
)

/**
 * Calculate statistics for a list of spans.
 */
fun List<SpanEntry>.stats(): SpanStats {
    if (isEmpty()) return SpanStats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0)

    val durations = map { it.durationMs }.sorted()
    val errorCount = count { it.isError }

    return SpanStats(
        count = size,
        totalDurationMs = durations.sum(),
        avgDurationMs = durations.average(),
        p50Ms = percentile(durations, 0.50),
        p95Ms = percentile(durations, 0.95),
        p99Ms = percentile(durations, 0.99),
        errorCount = errorCount,
        errorRate = errorCount.toDouble() / size
    )
}

private fun percentile(sorted: List<Double>, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    val index = ((sorted.size - 1) * p).toInt()
    return sorted[index]
}

/**
 * Extension to convert raw Datadog API response to SpanEntry.
 */
fun Map<String, Any>.toSpanEntry(): SpanEntry {
    val attrs = (this["attributes"] as? Map<*, *>)?.mapKeys { it.key.toString() }
        ?.mapValues { it.value } ?: emptyMap<String, Any?>()

    val spanKindStr = attrs["span.kind"]?.toString()?.uppercase()
        ?: attrs["spanKind"]?.toString()?.uppercase()

    val spanKind = try {
        spanKindStr?.let { SpanKind.valueOf(it) } ?: SpanKind.UNKNOWN
    } catch (e: IllegalArgumentException) {
        SpanKind.UNKNOWN
    }

    return SpanEntry(
        traceId = attrs["trace_id"]?.toString() ?: this["id"]?.toString() ?: "",
        spanId = attrs["span_id"]?.toString(),
        service = attrs["service"]?.toString() ?: "",
        resource = attrs["resource"]?.toString() ?: attrs["resource_name"]?.toString() ?: "",
        name = attrs["name"]?.toString(),
        timestamp = parseTimestamp(attrs["timestamp"] ?: attrs["start"]),
        duration = (attrs["duration"] as? Number)?.toLong() ?: 0L,
        spanKind = spanKind,
        isError = (attrs["error"] as? Number)?.toInt() == 1 || attrs["error"] == true,
        statusCode = (attrs["http.status_code"] as? Number)?.toInt(),
        peerService = attrs["peer.service"]?.toString(),
        attributes = attrs.filterValues { it != null }.mapValues { it.value!! }
    )
}

private fun parseTimestamp(value: Any?): Instant = when (value) {
    is String -> try { Instant.parse(value) } catch (e: Exception) { Instant.now() }
    is Number -> Instant.ofEpochMilli(value.toLong())
    else -> Instant.now()
}
