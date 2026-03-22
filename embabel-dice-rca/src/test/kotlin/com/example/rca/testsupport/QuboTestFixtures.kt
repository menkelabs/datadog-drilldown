package com.example.rca.testsupport

import com.example.rca.dice.qubo.DiceLeapPythonSolver
import com.example.rca.dice.qubo.QuboIntegrationProperties
import com.example.rca.dice.qubo.QuboObservationHelper
import com.example.rca.dice.qubo.QuboReportEnricher
import com.example.rca.dice.qubo.QuboSolverMetrics
import com.example.rca.dice.qubo.RcaCandidateToQuboInstanceMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry

/** [QuboReportEnricher] with QUBO disabled — for manual construction of [com.example.rca.agent.RcaAgent] in tests. */
fun disabledQuboReportEnricher(): QuboReportEnricher {
    val p = QuboIntegrationProperties(enabled = false)
    val metrics = QuboSolverMetrics(SimpleMeterRegistry())
    val obs = QuboObservationHelper(ObservationRegistry.create())
    return QuboReportEnricher(
        p,
        RcaCandidateToQuboInstanceMapper(),
        DiceLeapPythonSolver(p, metrics, obs),
        metrics,
    )
}
