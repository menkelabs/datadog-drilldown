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
    private val metrics: QuboSolverMetrics,
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
        val nEdges = { b: QuboInstanceBuild ->
            b.payload.conflicts.size + b.payload.dependencies.size
        }

        if (build == null) {
            baseFindings["qubo"] = mapOf(
                "enabled" to true,
                "skipped" to true,
                "reason" to "no_candidates",
            )
            metrics.recordEnrichment("skipped_no_candidates")
            telemetry(
                context.id, "1", "skipped", "no_candidates", "none",
                0, 0, 0L, null, null, null, null,
                "skipped_no_candidates", null, null,
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
            metrics.recordEnrichment("skipped_rollover")
            telemetry(
                context.id, "1", "heuristic_only", strategy.reason, "none",
                build.payload.entities.size, nEdges(build), 0L, null, null, null, null,
                "skipped_rollover", null, null,
            )
            return report.copy(findings = baseFindings)
        }

        val temp = Files.createTempFile("qubo-instance-", ".json")
        try {
            json.writeValue(temp.toFile(), build.payload)
            val sw = pythonSolver.solve(temp, forceStrategy = "qubo")
            when (val r = sw.result) {
                is SolveResult.Failure -> {
                    quboBlock["error"] = r.message
                    if (properties.fallbackOnSolverFailure && !properties.failOnSolverError) {
                        applyHeuristicFallback(quboBlock, report)
                        baseFindings["qubo"] = quboBlock
                        metrics.recordEnrichment("fallback_heuristic")
                        telemetry(
                            context.id,
                            instanceEncoding(build),
                            "qubo",
                            strategy.reason,
                            "local_classical",
                            build.payload.entities.size,
                            nEdges(build),
                            sw.totalSubprocessMs,
                            null,
                            null,
                            null,
                            null,
                            "fallback_heuristic",
                            r.message,
                            sw.attemptsUsed,
                        )
                        @Suppress("UNCHECKED_CAST")
                        val titles = quboBlock["selected_candidate_titles"] as List<String>
                        val extra = if (titles.isNotEmpty()) {
                            listOf(
                                "QUBO solver failed; heuristic shortlist: " + titles.joinToString(", "),
                            )
                        } else {
                            emptyList()
                        }
                        log.warn("QUBO solver failed for incident {}: {}", context.id, r.message)
                        return report.copy(findings = baseFindings, recommendations = report.recommendations + extra)
                    }
                    baseFindings["qubo"] = quboBlock
                    metrics.recordEnrichment("failure")
                    telemetry(
                        context.id,
                        instanceEncoding(build),
                        "qubo",
                        strategy.reason,
                        "local_classical",
                        build.payload.entities.size,
                        nEdges(build),
                        sw.totalSubprocessMs,
                        null,
                        null,
                        null,
                        null,
                        "failure",
                        r.message,
                        sw.attemptsUsed,
                    )
                    if (properties.failOnSolverError) {
                        error("QUBO solver failed: ${r.message}")
                    }
                    log.warn("QUBO solver failed for incident {}: {}", context.id, r.message)
                    return report.copy(findings = baseFindings)
                }
                is SolveResult.Success -> {
                    val rec = r.record
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
                    metrics.recordEnrichment("success")
                    telemetry(
                        context.id,
                        rec.encodingVersion ?: "1",
                        rec.strategyChoice,
                        rec.strategyReason,
                        rec.solverMode,
                        build.payload.entities.size,
                        nEdges(build),
                        sw.totalSubprocessMs,
                        rec.runtimeMs,
                        rec.objective,
                        rec.baselineObjective,
                        rec.vsBaselineDelta,
                        "success",
                        null,
                        sw.attemptsUsed,
                    )
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
                }
            }
        } finally {
            try {
                Files.deleteIfExists(temp)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    private fun instanceEncoding(b: QuboInstanceBuild): String = b.payload.encodingVersion ?: "1"

    private fun applyHeuristicFallback(quboBlock: MutableMap<String, Any>, report: Report) {
        quboBlock["fallback"] = true
        quboBlock["fallback_reason"] = "solver_error"
        val top = report.candidates
            .sortedByDescending { it.score }
            .take(properties.fallbackCandidateCount.coerceAtLeast(1))
            .map { it.title }
        quboBlock["selected_candidate_titles"] = top
    }

    private fun telemetry(
        caseId: String,
        encodingVersion: String,
        strategyChoice: String,
        strategyReason: String,
        solverMode: String,
        nCandidates: Int,
        nConstraintEdges: Int,
        subprocessMs: Long,
        runtimeMs: Double?,
        objective: Double?,
        baselineObjective: Double?,
        vsBaselineDelta: Double?,
        outcome: String,
        error: String?,
        attempt: Int?,
    ) {
        if (!properties.structuredTelemetryLog) return
        QuboStructuredTelemetry.logV1(
            log,
            caseId,
            encodingVersion,
            strategyChoice,
            strategyReason,
            solverMode,
            nCandidates,
            nConstraintEdges,
            subprocessMs,
            runtimeMs,
            objective,
            baselineObjective,
            vsBaselineDelta,
            outcome,
            error,
            attempt,
        )
    }

    private fun sanitizeInstanceId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]+"), "_").take(120).ifEmpty { "incident" }
}
