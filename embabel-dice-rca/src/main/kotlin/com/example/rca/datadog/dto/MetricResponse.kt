package com.example.rca.datadog.dto

/**
 * Response from Datadog metrics query API.
 */
data class MetricResponse(
    val series: List<MetricSeries> = emptyList(),
    val status: String? = null,
    val error: String? = null
)

/**
 * A single metric time series.
 */
data class MetricSeries(
    val metric: String? = null,
    val displayName: String? = null,
    val pointlist: List<MetricPoint> = emptyList(),
    val scope: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * A single data point in a time series.
 */
data class MetricPoint(
    val timestamp: Long,  // milliseconds since epoch
    val value: Double
)

/**
 * Summary statistics for a metric query result.
 */
data class MetricSummary(
    val value: Double?,           // Average or aggregate value
    val peakTs: Long?,            // Timestamp of peak value (epoch seconds)
    val peakValue: Double?,       // Peak value
    val pointCount: Int,          // Number of data points
    val min: Double?,
    val max: Double?
) {
    companion object {
        fun empty() = MetricSummary(
            value = null,
            peakTs = null,
            peakValue = null,
            pointCount = 0,
            min = null,
            max = null
        )
    }
}

/**
 * Summarize a metric response into statistics.
 */
fun MetricResponse.summarize(): MetricSummary {
    val allPoints = series.flatMap { it.pointlist }
    if (allPoints.isEmpty()) return MetricSummary.empty()

    val values = allPoints.map { it.value }
    val peak = allPoints.maxByOrNull { it.value }

    return MetricSummary(
        value = values.average(),
        peakTs = peak?.let { it.timestamp / 1000 },  // Convert to epoch seconds
        peakValue = peak?.value,
        pointCount = allPoints.size,
        min = values.minOrNull(),
        max = values.maxOrNull()
    )
}

/**
 * Calculate percent change between baseline and incident values.
 */
fun percentChange(baseline: Double?, incident: Double?): Double? {
    if (baseline == null || incident == null) return null
    if (baseline == 0.0) {
        return if (incident == 0.0) 0.0 else Double.POSITIVE_INFINITY
    }
    return ((incident - baseline) / baseline) * 100.0
}

/**
 * Extension to parse raw Datadog API response.
 */
fun Map<String, Any>.toMetricResponse(): MetricResponse {
    val seriesList = (this["series"] as? List<*>)?.mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        val pointlist = (map["pointlist"] as? List<*>)?.mapNotNull { pt ->
            when (pt) {
                is List<*> -> MetricPoint(
                    timestamp = (pt.getOrNull(0) as? Number)?.toLong() ?: 0L,
                    value = (pt.getOrNull(1) as? Number)?.toDouble() ?: 0.0
                )
                else -> null
            }
        } ?: emptyList()

        MetricSeries(
            metric = map["metric"]?.toString(),
            displayName = map["display_name"]?.toString(),
            pointlist = pointlist,
            scope = map["scope"]?.toString(),
            tags = (map["tag_set"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        )
    } ?: emptyList()

    return MetricResponse(
        series = seriesList,
        status = this["status"]?.toString(),
        error = this["error"]?.toString()
    )
}
