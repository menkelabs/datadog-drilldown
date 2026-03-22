package com.example.rca.dice.qubo

import org.slf4j.Logger

/** Single-line structured log for Datadog / Loki grep (M2b C2). See docs/QUBO_METRICS_V1.md. */
object QuboStructuredTelemetry {
    fun logV1(
        log: Logger,
        caseId: String,
        encodingVersion: String,
        strategyChoice: String,
        strategyReason: String,
        solverMode: String,
        nCandidates: Int,
        nConstraintEdges: Int,
        subprocessMs: Long,
        runtimeMs: Double?,
        objective: Double?,
        baselineObjective: Double?,
        vsBaselineDelta: Double?,
        outcome: String,
        error: String?,
        attempt: Int?,
    ) {
        log.info(
            "qubo_telemetry case_id={} encoding_version={} strategy_choice={} strategy_reason={} " +
                "solver_mode={} n_candidates={} n_constraint_edges={} subprocess_ms={} runtime_ms={} " +
                "objective={} baseline_objective={} vs_baseline_delta={} outcome={} error={} attempt={}",
            caseId,
            encodingVersion,
            strategyChoice,
            strategyReason,
            solverMode,
            nCandidates,
            nConstraintEdges,
            subprocessMs,
            runtimeMs ?: "",
            objective ?: "",
            baselineObjective ?: "",
            vsBaselineDelta ?: "",
            outcome,
            error ?: "",
            attempt ?: "",
        )
    }
}
