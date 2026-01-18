package com.example.rca.datadog.dto

import java.time.Instant

/**
 * Represents a log entry from Datadog.
 */
data class LogEntry(
    val id: String,
    val timestamp: Instant,
    val service: String? = null,
    val host: String? = null,
    val status: String? = null,
    val message: String,
    val attributes: Map<String, Any> = emptyMap(),
    val tags: List<String> = emptyList()
) {
    /**
     * Extract error information from log attributes.
     */
    fun errorType(): String? =
        attributes["error.type"] as? String
            ?: attributes["error_type"] as? String

    fun errorMessage(): String? =
        attributes["error.message"] as? String
            ?: attributes["error_message"] as? String

    fun errorStack(): String? =
        attributes["error.stack"] as? String
            ?: attributes["error_stack"] as? String

    /**
     * Extract the HTTP status code if present.
     */
    fun httpStatusCode(): Int? =
        (attributes["http.status_code"] as? Number)?.toInt()
            ?: (attributes["@http.status_code"] as? Number)?.toInt()

    /**
     * Check if this log represents an error.
     */
    fun isError(): Boolean =
        status?.lowercase() == "error"
            || attributes["@status"]?.toString()?.lowercase() == "error"
            || attributes["level"]?.toString()?.lowercase() == "error"
            || (httpStatusCode() ?: 0) >= 500

    /**
     * Generate a fingerprint for clustering similar logs.
     */
    fun fingerprint(): String {
        val normalized = normalizeMessage(message)
        return "${service ?: "unknown"}:${errorType() ?: "generic"}:${normalized.hashCode()}"
    }

    companion object {
        /**
         * Normalize a log message by replacing variable parts with placeholders.
         */
        fun normalizeMessage(message: String): String {
            return message
                // Replace UUIDs
                .replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "<UUID>")
                // Replace numbers
                .replace(Regex("\\b\\d+\\b"), "<NUM>")
                // Replace quoted strings
                .replace(Regex("\"[^\"]*\""), "<STR>")
                .replace(Regex("'[^']*'"), "<STR>")
                // Replace IP addresses
                .replace(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), "<IP>")
                // Collapse whitespace
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}

/**
 * Extension to convert raw Datadog API response to LogEntry.
 */
fun Map<String, Any>.toLogEntry(): LogEntry {
    val attrs = (this["attributes"] as? Map<*, *>)?.mapKeys { it.key.toString() }
        ?.mapValues { it.value ?: "" } ?: emptyMap<String, Any>()

    return LogEntry(
        id = this["id"]?.toString() ?: "",
        timestamp = parseTimestamp(attrs["timestamp"]),
        service = attrs["service"]?.toString(),
        host = attrs["host"]?.toString(),
        status = attrs["status"]?.toString() ?: attrs["@status"]?.toString(),
        message = attrs["message"]?.toString() ?: "",
        attributes = attrs,
        tags = extractTags(attrs["ddtags"])
    )
}

private fun parseTimestamp(value: Any?): Instant = when (value) {
    is String -> try { Instant.parse(value) } catch (e: Exception) { Instant.now() }
    is Number -> Instant.ofEpochMilli(value.toLong())
    else -> Instant.now()
}

private fun extractTags(ddtags: Any?): List<String> = when (ddtags) {
    is String -> ddtags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    is List<*> -> ddtags.mapNotNull { it?.toString() }
    else -> emptyList()
}
