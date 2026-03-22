package com.example.rca.dice.qubo

import com.example.rca.domain.Candidate
import com.example.rca.domain.CandidateKind
import com.example.rca.domain.IncidentContext
import com.example.rca.domain.Report
import com.example.rca.domain.ReportMeta
import com.example.rca.domain.Scope
import com.example.rca.domain.SeedType
import com.example.rca.domain.Windows
import com.example.rca.dice.qubo.QuboObservationHelper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuboReportEnricherTest {

    private val mapper = RcaCandidateToQuboInstanceMapper()

    private fun enricher(props: QuboIntegrationProperties): QuboReportEnricher {
        val reg = SimpleMeterRegistry()
        val m = QuboSolverMetrics(reg)
        val obs = QuboObservationHelper(ObservationRegistry.create())
        return QuboReportEnricher(props, mapper, DiceLeapPythonSolver(props, m, obs), m)
    }

    @Test
    fun `when disabled returns report unchanged`() {
        val props = QuboIntegrationProperties(enabled = false)
        val enricher = enricher(props)
        val report = minimalReport()
        val ctx = IncidentContext("inc-1", testWindows(), Scope(service = "api", env = "prod"))
        val out = enricher.enrich(report, ctx)
        assertEquals(report, out)
    }

    @Test
    fun `when enabled and no candidates attaches skipped`() {
        val props = QuboIntegrationProperties(enabled = true, diceLeapPocRoot = "/tmp")
        val enricher = enricher(props)
        val report = minimalReport(candidates = emptyList())
        val ctx = IncidentContext("inc-1", testWindows(), Scope(service = "api", env = "prod"))
        val out = enricher.enrich(report, ctx)
        @Suppress("UNCHECKED_CAST")
        val qubo = out.findings["qubo"] as Map<*, *>
        assertEquals(true, qubo["enabled"])
        assertEquals(true, qubo["skipped"])
        assertEquals("no_candidates", qubo["reason"])
    }

    @Test
    fun `when solver fails and fallback on uses top candidates`() {
        val five = (1..5).map { i ->
            Candidate(CandidateKind.LOGS, "Cand-$i", 0.5 + i * 0.01)
        }
        val props = QuboIntegrationProperties(
            enabled = true,
            diceLeapPocRoot = "",
            fallbackOnSolverFailure = true,
            failOnSolverError = false,
            structuredTelemetryLog = false,
            maxSubprocessAttempts = 1,
        )
        val enricher = enricher(props)
        val report = minimalReport(candidates = five)
        val ctx = IncidentContext("inc-1", testWindows(), Scope(service = "api", env = "prod"))
        val out = enricher.enrich(report, ctx)
        @Suppress("UNCHECKED_CAST")
        val qubo = out.findings["qubo"] as Map<*, *>
        assertEquals(true, qubo["fallback"])
        @Suppress("UNCHECKED_CAST")
        val titles = qubo["selected_candidate_titles"] as List<*>
        assertEquals(3, titles.size)
        assertEquals(true, out.recommendations.any { it.contains("QUBO solver failed") })
    }

    @Test
    fun `when enabled and heuristic rollover skips python`() {
        val props = QuboIntegrationProperties(enabled = true, diceLeapPocRoot = "/tmp")
        val enricher = enricher(props)
        // 2 candidates, small clique => few edges, below rollover
        val cands = listOf(
            Candidate(CandidateKind.LOGS, "A", 0.9),
            Candidate(CandidateKind.LOGS, "B", 0.8),
        )
        val report = minimalReport(candidates = cands)
        val ctx = IncidentContext("inc-1", testWindows(), Scope(service = "api", env = "prod"))
        val out = enricher.enrich(report, ctx)
        @Suppress("UNCHECKED_CAST")
        val qubo = out.findings["qubo"] as Map<*, *>
        assertEquals(true, qubo["skipped"])
        assertEquals("rollover_heuristic_only", qubo["skipped_reason"])
        assertNull(qubo["solve_record"])
    }

    companion object {
        fun testWindows(): Windows = Windows.endingAt(Instant.parse("2026-01-01T12:00:00Z"), 30, 30)

        fun minimalReport(candidates: List<Candidate> = emptyList()): Report = Report(
            meta = ReportMeta(SeedType.MANUAL, Instant.parse("2026-01-01T00:00:00Z"), "datadoghq.com", emptyMap()),
            windows = testWindows(),
            scope = Scope(service = "s", env = "e"),
            symptoms = emptyList(),
            findings = mutableMapOf("k" to "v"),
            recommendations = emptyList(),
            candidates = candidates,
        )
    }
}
