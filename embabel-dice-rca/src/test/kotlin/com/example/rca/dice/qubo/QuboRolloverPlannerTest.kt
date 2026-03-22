package com.example.rca.dice.qubo

import kotlin.test.Test
import kotlin.test.assertEquals

class QuboRolloverPlannerTest {

    @Test
    fun `tier simple forces heuristic`() {
        val d = QuboRolloverPlanner.plan("simple", 100, 100, 100)
        assertEquals(QuboStrategyDecision.StrategyChoice.HEURISTIC_ONLY, d.choice)
        assertEquals("fixture_tier_simple", d.reason)
    }

    @Test
    fun `tier complex forces qubo`() {
        val d = QuboRolloverPlanner.plan("complex", 1, 0, 0)
        assertEquals(QuboStrategyDecision.StrategyChoice.QUBO, d.choice)
        assertEquals("fixture_tier_complex", d.reason)
    }

    @Test
    fun `rollover by entity count`() {
        val d = QuboRolloverPlanner.plan(null, 13, 0, 0)
        assertEquals(QuboStrategyDecision.StrategyChoice.QUBO, d.choice)
        assertEquals("rollover_metrics(n=13,edges=0)", d.reason)
    }

    @Test
    fun `rollover by edge count`() {
        val d = QuboRolloverPlanner.plan(null, 5, 9, 0)
        assertEquals(QuboStrategyDecision.StrategyChoice.QUBO, d.choice)
        assertEquals("rollover_metrics(n=5,edges=9)", d.reason)
    }

    @Test
    fun `below rollover`() {
        val d = QuboRolloverPlanner.plan(null, 5, 3, 4)
        assertEquals(QuboStrategyDecision.StrategyChoice.HEURISTIC_ONLY, d.choice)
        assertEquals("below_rollover(n=5,edges=7)", d.reason)
    }
}
