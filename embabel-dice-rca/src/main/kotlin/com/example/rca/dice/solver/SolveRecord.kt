package com.example.rca.dice.solver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * Kotlin mirror of dice-leap-poc [SolveRecord] JSON lines (JSONL contract).
 * Ingest from dice-leap-poc runs directory or L1 mirror under test-reports/solver-runs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SolveRecord(
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
