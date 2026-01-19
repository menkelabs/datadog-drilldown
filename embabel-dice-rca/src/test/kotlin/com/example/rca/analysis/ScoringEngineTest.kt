package com.example.rca.analysis

import com.example.rca.domain.Candidate
import com.example.rca.domain.CandidateKind
import com.example.rca.domain.LogCluster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class ScoringEngineTest {

    private val engine = ScoringEngine()

    @Test
    fun `test merge and rank candidates`() {
        val logCandidate = Candidate(
            kind = CandidateKind.LOGS,
            title = "Error log",
            score = 0.8,
            evidence = emptyMap()
        )
        val apmCandidate = Candidate(
            kind = CandidateKind.DEPENDENCY,
            title = "Slow DB",
            score = 0.9,
            evidence = emptyMap()
        )

        val result = engine.mergeAndRank(listOf(logCandidate), listOf(apmCandidate))

        assertEquals(2, result.size)
        assertEquals(CandidateKind.DEPENDENCY, result[0].kind) // 0.9 > 0.8
    }
}
