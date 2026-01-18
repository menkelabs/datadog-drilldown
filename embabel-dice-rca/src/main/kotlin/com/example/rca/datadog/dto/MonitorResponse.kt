package com.example.rca.datadog.dto

/**
 * Response from Datadog monitor API.
 */
data class MonitorResponse(
    val id: Long,
    val name: String,
    val type: String,
    val query: String,
    val message: String? = null,
    val tags: List<String> = emptyList(),
    val state: MonitorState? = null,
    val options: Map<String, Any> = emptyMap()
)

/**
 * Monitor state information.
 */
data class MonitorState(
    val overallState: String? = null,
    val groups: Map<String, GroupState> = emptyMap()
)

/**
 * State of a monitor group.
 */
data class GroupState(
    val status: String,
    val lastTriggeredTs: Long? = null,
    val lastResolvedTs: Long? = null
)

/**
 * Extension to convert raw Datadog API response to MonitorResponse.
 */
fun Map<String, Any>.toMonitorResponse(): MonitorResponse {
    val stateMap = this["state"] as? Map<*, *>
    val groupsMap = (stateMap?.get("groups") as? Map<*, *>)?.mapKeys { it.key.toString() }
        ?.mapValues { entry ->
            val gmap = entry.value as? Map<*, *>
            GroupState(
                status = gmap?.get("status")?.toString() ?: "unknown",
                lastTriggeredTs = (gmap?.get("last_triggered_ts") as? Number)?.toLong(),
                lastResolvedTs = (gmap?.get("last_resolved_ts") as? Number)?.toLong()
            )
        } ?: emptyMap()

    return MonitorResponse(
        id = (this["id"] as? Number)?.toLong() ?: 0L,
        name = this["name"]?.toString() ?: "",
        type = this["type"]?.toString() ?: "",
        query = this["query"]?.toString() ?: "",
        message = this["message"]?.toString(),
        tags = (this["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
        state = MonitorState(
            overallState = stateMap?.get("overall_state")?.toString(),
            groups = groupsMap
        ),
        options = (this["options"] as? Map<*, *>)?.mapKeys { it.key.toString() }
            ?.mapValues { it.value ?: "" } ?: emptyMap()
    )
}
