package com.example.rca.domain

import java.time.Duration
import java.time.Instant

/**
 * Represents a time window for analysis.
 */
data class TimeWindow(
    val start: Instant,
    val end: Instant
) {
    init {
        require(end > start) { "End must be after start" }
    }

    val duration: Duration
        get() = Duration.between(start, end)

    val startEpoch: Long
        get() = start.epochSecond

    val endEpoch: Long
        get() = end.epochSecond

    fun contains(instant: Instant): Boolean =
        instant >= start && instant <= end

    fun overlaps(other: TimeWindow): Boolean =
        start < other.end && end > other.start
}

/**
 * Holds the incident and baseline windows for analysis.
 */
data class Windows(
    val incident: TimeWindow,
    val baseline: TimeWindow,
    val anchor: Instant
) {
    companion object {
        /**
         * Create windows ending at the given anchor timestamp.
         */
        fun endingAt(
            anchor: Instant,
            windowMinutes: Int,
            baselineMinutes: Int
        ): Windows {
            val incidentEnd = anchor
            val incidentStart = anchor.minus(Duration.ofMinutes(windowMinutes.toLong()))
            val baselineEnd = incidentStart
            val baselineStart = baselineEnd.minus(Duration.ofMinutes(baselineMinutes.toLong()))

            return Windows(
                incident = TimeWindow(incidentStart, incidentEnd),
                baseline = TimeWindow(baselineStart, baselineEnd),
                anchor = anchor
            )
        }

        /**
         * Create windows from explicit start and end times.
         */
        fun fromRange(start: Instant, end: Instant): Windows {
            val duration = Duration.between(start, end)
            val baselineStart = start.minus(duration)

            return Windows(
                incident = TimeWindow(start, end),
                baseline = TimeWindow(baselineStart, start),
                anchor = end
            )
        }
    }

    fun toMap(): Map<String, Any> = mapOf(
        "incident" to mapOf(
            "start" to incident.start.toString(),
            "end" to incident.end.toString(),
            "start_epoch" to incident.startEpoch,
            "end_epoch" to incident.endEpoch
        ),
        "baseline" to mapOf(
            "start" to baseline.start.toString(),
            "end" to baseline.end.toString(),
            "start_epoch" to baseline.startEpoch,
            "end_epoch" to baseline.endEpoch
        ),
        "anchor" to anchor.toString()
    )
}
