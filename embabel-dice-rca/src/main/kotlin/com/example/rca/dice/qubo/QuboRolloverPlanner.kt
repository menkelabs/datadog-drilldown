package com.example.rca.dice.qubo

/**
 * Kotlin mirror of [dice_leap_poc.strategy.plan_strategy] for deciding heuristic vs QUBO path
 * before calling Python.
 */
data class RolloverConfig(
    val maxCandidatesForHeuristic: Int = 12,
    val maxConstraintEdgesForHeuristic: Int = 8,
)

data class QuboStrategyDecision(
    val choice: StrategyChoice,
    val reason: String,
) {
    enum class StrategyChoice {
        HEURISTIC_ONLY,
        QUBO,
    }
}

object QuboRolloverPlanner {
    fun constraintEdgeCount(conflicts: Int, dependencies: Int): Int = conflicts + dependencies

    fun plan(
        tier: String?,
        nEntities: Int,
        nConflictEdges: Int,
        nDependencyEdges: Int,
        cfg: RolloverConfig = RolloverConfig(),
    ): QuboStrategyDecision {
        val edges = constraintEdgeCount(nConflictEdges, nDependencyEdges)
        when (tier?.lowercase()) {
            "simple" -> return QuboStrategyDecision(
                QuboStrategyDecision.StrategyChoice.HEURISTIC_ONLY,
                "fixture_tier_simple",
            )
            "complex" -> return QuboStrategyDecision(
                QuboStrategyDecision.StrategyChoice.QUBO,
                "fixture_tier_complex",
            )
        }
        if (nEntities > cfg.maxCandidatesForHeuristic || edges > cfg.maxConstraintEdgesForHeuristic) {
            return QuboStrategyDecision(
                QuboStrategyDecision.StrategyChoice.QUBO,
                "rollover_metrics(n=$nEntities,edges=$edges)",
            )
        }
        return QuboStrategyDecision(
            QuboStrategyDecision.StrategyChoice.HEURISTIC_ONLY,
            "below_rollover(n=$nEntities,edges=$edges)",
        )
    }
}
