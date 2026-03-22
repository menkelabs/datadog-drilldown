package com.example.rca.dice.qubo

import com.example.rca.domain.Candidate
import org.springframework.stereotype.Component

/**
 * Maps ranked RCA [Candidate]s into a [QuboInstancePayload] for dice-leap-poc.
 *
 * Heuristic (M2a): stable ids `c0..cN`; cost/signal derived from scores; pairwise conflicts among
 * the top-[maxMutuallyExclusive] candidates to approximate "pick a coherent subset" without full domain modeling.
 */
@Component
class RcaCandidateToQuboInstanceMapper {

    /**
     * Top-K candidates form a conflict clique. K=5 ⇒ 10 edges, exceeding Python default
     * rollover (8) so typical RCA sizes (≥5 candidates) take the QUBO path when tier is unset.
     */
    private val maxMutuallyExclusive: Int = 5

    fun build(
        instanceId: String,
        candidates: List<Candidate>,
        tier: String? = null,
        encodingVersion: String = "1",
        rolloverConfig: RolloverConfig = RolloverConfig(),
    ): QuboInstanceBuild? {
        if (candidates.isEmpty()) return null

        val ordered = candidates.sortedByDescending { it.score }
        val entities = ordered.mapIndexed { idx, c ->
            val id = "c$idx"
            // Higher score => lower diagonal cost contribution (cost - signal in QUBO), higher signal pull.
            val cost = (1.0 - c.score) * 5.0 + 0.5
            val signal = c.score * 8.0 + 0.5
            QuboEntityPayload(id = id, cost = cost, signal = signal)
        }

        val idByIndex = entities.mapIndexed { i, e -> i to e.id }.toMap()
        val k = minOf(maxMutuallyExclusive, entities.size)
        val conflicts = mutableListOf<List<String>>()
        for (i in 0 until k) {
            for (j in (i + 1) until k) {
                val a = idByIndex[i]!!
                val b = idByIndex[j]!!
                conflicts.add(listOf(a, b))
            }
        }

        val nConflicts = conflicts.size
        val nDeps = 0
        val decision = QuboRolloverPlanner.plan(tier, entities.size, nConflicts, nDeps, rolloverConfig)

        val description = ordered.take(8).joinToString("; ") { it.title }.take(500)

        return QuboInstanceBuild(
            payload = QuboInstancePayload(
                instanceId = instanceId,
                entities = entities,
                conflicts = conflicts,
                dependencies = emptyList(),
                tier = tier,
                description = description.ifEmpty { null },
                encodingVersion = encodingVersion,
            ),
            idToTitle = entities.mapIndexed { idx, e -> e.id to ordered[idx].title }.toMap(),
            strategyDecision = decision,
        )
    }
}

data class QuboInstanceBuild(
    val payload: QuboInstancePayload,
    /** Maps QUBO entity id (c0, …) back to candidate title for interpretation. */
    val idToTitle: Map<String, String>,
    val strategyDecision: QuboStrategyDecision,
)
