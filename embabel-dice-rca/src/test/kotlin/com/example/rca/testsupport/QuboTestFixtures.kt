package com.example.rca.testsupport

import com.example.rca.dice.qubo.DiceLeapPythonSolver
import com.example.rca.dice.qubo.QuboIntegrationProperties
import com.example.rca.dice.qubo.QuboReportEnricher
import com.example.rca.dice.qubo.RcaCandidateToQuboInstanceMapper

/** [QuboReportEnricher] with QUBO disabled — for manual construction of [com.example.rca.agent.RcaAgent] in tests. */
fun disabledQuboReportEnricher(): QuboReportEnricher {
    val p = QuboIntegrationProperties(enabled = false)
    return QuboReportEnricher(p, RcaCandidateToQuboInstanceMapper(), DiceLeapPythonSolver(p))
}
