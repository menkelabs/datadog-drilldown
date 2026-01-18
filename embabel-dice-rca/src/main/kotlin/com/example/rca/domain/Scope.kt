package com.example.rca.domain

import com.example.rca.datadog.dto.LogEntry

/**
 * Represents the scope of an incident analysis (service, environment, etc.).
 */
data class Scope(
    val service: String? = null,
    val env: String? = null,
    val host: String? = null,
    val tags: List<String> = emptyList()
) {
    /**
     * Generate a Datadog event tag query from this scope.
     */
    fun toEventTagQuery(): String? {
        val parts = mutableListOf<String>()
        service?.let { parts.add("service:$it") }
        env?.let { parts.add("env:$it") }
        host?.let { parts.add("host:$it") }
        parts.addAll(tags)
        return if (parts.isEmpty()) null else parts.joinToString(",")
    }

    /**
     * Generate a Datadog log query from this scope.
     */
    fun toLogQuery(additionalFilters: String? = null): String {
        val parts = mutableListOf<String>()
        service?.let { parts.add("service:$it") }
        env?.let { parts.add("env:$it") }
        host?.let { parts.add("host:$it") }
        additionalFilters?.let { parts.add(it) }
        return parts.joinToString(" ")
    }

    /**
     * Check if scope has minimum required fields for APM analysis.
     */
    fun isApmReady(): Boolean = service != null && env != null

    fun toMap(): Map<String, Any?> = mapOf(
        "service" to service,
        "env" to env,
        "host" to host,
        "tags" to tags
    )

    companion object {
        /**
         * Extract scope from Datadog monitor tags.
         */
        fun fromMonitorTags(tags: List<String>): Scope {
            var service: String? = null
            var env: String? = null
            var host: String? = null
            val otherTags = mutableListOf<String>()

            for (tag in tags) {
                when {
                    tag.startsWith("service:") -> service = tag.substringAfter("service:")
                    tag.startsWith("env:") -> env = tag.substringAfter("env:")
                    tag.startsWith("host:") -> host = tag.substringAfter("host:")
                    else -> otherTags.add(tag)
                }
            }

            return Scope(service, env, host, otherTags)
        }

        /**
         * Extract scope from log entries by finding common attributes.
         */
        fun fromLogEntries(logs: List<LogEntry>): Scope {
            if (logs.isEmpty()) return Scope()

            val services = mutableSetOf<String>()
            val envs = mutableSetOf<String>()

            for (log in logs) {
                log.service?.let { services.add(it) }
                
                // Try to extract env from tags
                log.tags.forEach { tag ->
                    if (tag.startsWith("env:")) {
                        envs.add(tag.substringAfter("env:"))
                    }
                }
            }

            return Scope(
                service = services.singleOrNull(),
                env = envs.singleOrNull()
            )
        }

        /**
         * Extract scope from raw log entries (map format) by finding common attributes.
         */
        fun fromLogs(logs: List<Map<String, Any>>): Scope {
            if (logs.isEmpty()) return Scope()

            val services = mutableSetOf<String>()
            val envs = mutableSetOf<String>()

            for (log in logs) {
                val attrs = log["attributes"] as? Map<*, *> ?: continue
                (attrs["service"] as? String)?.let { services.add(it) }
                
                // Try to extract env from ddtags
                val ddtags = attrs["ddtags"] as? String
                ddtags?.split(",")?.forEach { tag ->
                    if (tag.startsWith("env:")) {
                        envs.add(tag.substringAfter("env:"))
                    }
                }
            }

            return Scope(
                service = services.singleOrNull(),
                env = envs.singleOrNull()
            )
        }
    }
}
