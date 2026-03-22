package com.example.testreport.solver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/** One line from dice-leap-poc JSONL (`SolveRecord`). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SolverRunLineDto(
    val instanceId: String,
    val solverMode: String,
    val strategyChoice: String,
    val strategyReason: String,
    val nVars: Int,
    val objective: Double,
    val selectedDecisions: List<String>,
    val runtimeMs: Double,
    val baselineObjective: Double,
    val vsBaselineDelta: Double,
    val encodingVersion: String? = null,
    val tier: String? = null,
)
