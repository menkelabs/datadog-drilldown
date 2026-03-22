package com.example.rca.dice.qubo

import org.springframework.boot.context.properties.ConfigurationProperties

/** Feature-flagged bridge to dice-leap-poc (Python) for QUBO solve after RCA (ADR 0002). */
@ConfigurationProperties(prefix = "embabel.rca.qubo")
data class QuboIntegrationProperties(
    /** When false (default), no Python subprocess and reports are unchanged. */
    val enabled: Boolean = false,
    /** e.g. python3 */
    val pythonExecutable: String = "python3",
    /**
     * Absolute or relative path to the `dice-leap-poc` directory (contains `dice_leap_poc/` and `scripts/solve_json.py`).
     * Empty uses working directory at runtime (fragile); set explicitly in deployment.
     */
    val diceLeapPocRoot: String = "",
    val subprocessTimeoutSeconds: Long = 120,
    /** Subprocess attempts before giving up (M2c). */
    val maxSubprocessAttempts: Int = 2,
    /** Delay between subprocess attempts (ms). */
    val subprocessRetryDelayMs: Long = 200,
    /** When true, enrichment throws if the subprocess fails; when false, attaches error details under findings.qubo. */
    val failOnSolverError: Boolean = false,
    /**
     * When subprocess fails and [failOnSolverError] is false, fill `selected_candidate_titles` from top RCA candidates (M2c fallback).
     */
    val fallbackOnSolverFailure: Boolean = true,
    val fallbackCandidateCount: Int = 3,
    /** Emit `qubo_telemetry` structured log lines when QUBO path runs or skips (M2b). */
    val structuredTelemetryLog: Boolean = true,
)
