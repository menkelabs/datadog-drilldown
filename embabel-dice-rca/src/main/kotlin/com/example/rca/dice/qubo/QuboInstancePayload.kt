package com.example.rca.dice.qubo

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * JSON shape accepted by [dice_leap_poc.instance.Instance.from_dict] (snake_case in wire format).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class QuboInstancePayload(
    val instanceId: String,
    val entities: List<QuboEntityPayload>,
    val conflicts: List<List<String>> = emptyList(),
    val dependencies: List<List<String>> = emptyList(),
    val conflictPenalty: Double = 5.0,
    val dependencyPenalty: Double = 10.0,
    val tier: String? = null,
    val description: String? = null,
    val encodingVersion: String? = null,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class QuboEntityPayload(
    val id: String,
    val cost: Double,
    val signal: Double,
)
