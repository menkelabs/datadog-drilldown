package com.example.rca.dice.qubo

import com.example.rca.domain.IncidentContext
import com.example.rca.domain.Report
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files

/**
 * After RCA [Report] is built, optionally run dice-leap-poc and attach results under `findings["qubo"]`.
 */
@Service
class QuboReportEnricher(
    private val properties: QuboIntegrationProperties,
    private val mapper: RcaCandidateToQuboInstanceMapper,
    private val pythonSolver: DiceLeapPythonSolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json: ObjectMapper = jacksonObjectMapper()
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    fun enrich(report: Report, context: IncidentContext): Report {
        if (!properties.enabled) {
            return report
        }

        val instanceId = sanitizeInstanceId(context.id)
        val build = mapper.build(
            instanceId = instanceId,
            candidates = report.candidates,
            tier = null,
            encodingVersion = "1",
        )

        val baseFindings = report.findings.toMutableMap()

        if (build == null) {
            baseFindings["qubo"] = mapOf(
                "enabled" to true,
                "skipped" to true,
                "reason" to "no_candidates",
            )
            return report.copy(findings = baseFindings)
        }

        val strategy = build.strategyDecision
        val quboBlock = mutableMapOf<String, Any>(
            "enabled" to true,
            "strategy_choice" to strategy.choice.name.lowercase(),
            "strategy_reason" to strategy.reason,
            "entity_ids" to build.idToTitle,
        )

        if (strategy.choice == QuboStrategyDecision.StrategyChoice.HEURISTIC_ONLY) {
            quboBlock["skipped"] = true
            quboBlock["skipped_reason"] = "rollover_heuristic_only"
            baseFindings["qubo"] = quboBlock
            return report.copy(findings = baseFindings)
        }

        val temp = Files.createTempFile("qubo-instance-", ".json")
        try {
            json.writeValue(temp.toFile(), build.payload)
            val py = when (val r = pythonSolver.solve(temp, forceStrategy = "qubo")) {
                is SolveResult.Success -> r
                is SolveResult.Failure -> {
                    quboBlock["error"] = r.message
                    baseFindings["qubo"] = quboBlock
                    if (properties.failOnSolverError) {
                        error("QUBO solver failed: ${r.message}")
                    }
                    log.warn("QUBO solver failed for incident {}: {}", context.id, r.message)
                    return report.copy(findings = baseFindings)
                }
            }

            val rec = py.record
            quboBlock["solve_record"] = mapOf(
                "instance_id" to rec.instanceId,
                "solver_mode" to rec.solverMode,
                "strategy_choice" to rec.strategyChoice,
                "strategy_reason" to rec.strategyReason,
                "n_vars" to rec.nVars,
                "objective" to rec.objective,
                "selected_decisions" to rec.selectedDecisions,
                "runtime_ms" to rec.runtimeMs,
                "baseline_objective" to rec.baselineObjective,
                "vs_baseline_delta" to rec.vsBaselineDelta,
                "encoding_version" to rec.encodingVersion,
                "tier" to rec.tier,
            )
            val selectedTitles = rec.selectedDecisions.mapNotNull { build.idToTitle[it] }
            quboBlock["selected_candidate_titles"] = selectedTitles

            baseFindings["qubo"] = quboBlock

            val extraRec = if (selectedTitles.isNotEmpty()) {
                listOf(
                    "QUBO shortlist (dice-leap-poc): prioritize " + selectedTitles.joinToString(", "),
                )
            } else {
                emptyList()
            }

            return report.copy(
                findings = baseFindings,
                recommendations = report.recommendations + extraRec,
            )
        } finally {
            try {
                Files.deleteIfExists(temp)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    private fun sanitizeInstanceId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(120).ifEmpty { "incident" }
}
