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
    /** When true, enrichment throws if the subprocess fails; when false, attaches error details under findings.qubo. */
    val failOnSolverError: Boolean = false,
)
