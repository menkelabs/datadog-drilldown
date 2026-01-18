package com.example.rca.datadog.dto

import java.time.Instant

/**
 * Response from Datadog events API.
 */
data class EventResponse(
    val events: List<EventEntry> = emptyList()
)

/**
 * Represents an event from Datadog (deploy, alert, etc.).
 */
data class EventEntry(
    val id: Long,
    val title: String,
    val text: String,
    val dateHappened: Instant,
    val alertType: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val url: String? = null
) {
    /**
     * Check if this is a deployment event.
     */
    fun isDeployment(): Boolean =
        title.contains("deploy", ignoreCase = true)
            || tags.any { it.contains("deploy", ignoreCase = true) }
            || source?.contains("deploy", ignoreCase = true) == true

    /**
     * Check if this is a configuration change event.
     */
    fun isConfigChange(): Boolean =
        title.contains("config", ignoreCase = true)
            || tags.any { it.contains("config", ignoreCase = true) }

    /**
     * Check if this is an autoscaling event.
     */
    fun isAutoscaling(): Boolean =
        title.contains("scale", ignoreCase = true)
            || tags.any { it.contains("autoscal", ignoreCase = true) }
}

/**
 * Extension to convert raw Datadog API response to EventResponse.
 */
fun Map<String, Any>.toEventResponse(): EventResponse {
    val eventsList = (this["events"] as? List<*>)?.mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null

        EventEntry(
            id = (map["id"] as? Number)?.toLong() ?: 0L,
            title = map["title"]?.toString() ?: "",
            text = map["text"]?.toString() ?: "",
            dateHappened = Instant.ofEpochSecond((map["date_happened"] as? Number)?.toLong() ?: 0L),
            alertType = map["alert_type"]?.toString(),
            source = map["source"]?.toString(),
            tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            url = map["url"]?.toString()
        )
    } ?: emptyList()

    return EventResponse(events = eventsList)
}
