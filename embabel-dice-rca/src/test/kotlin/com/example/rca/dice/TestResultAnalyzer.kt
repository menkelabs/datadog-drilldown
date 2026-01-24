package com.example.rca.dice

import org.slf4j.LoggerFactory

/**
 * Result of diving into test reports: findings and concrete param suggestions for what-if tuning
 * (e.g. keyword coverage, AI temp, expected keywords).
 */
data class TestAnalysisResult(
        val findings: List<AnalysisFinding>,
        val suggestions: List<ParamSuggestion>,
        val summary: String
) {
    fun hasSuggestions(): Boolean = suggestions.isNotEmpty()
    fun hasFindings(): Boolean = findings.isNotEmpty()
}

data class AnalysisFinding(
        val kind: FindingKind,
        val message: String,
        val detail: String? = null,
        val affectedTests: Int = 0,
        val scenarioId: String? = null
)

enum class FindingKind {
    KEYWORD_COVERAGE,
    MISSING_KEYWORDS,
    COMPONENT_CAUSE_MISMATCH,
    TEMPERATURE_CORRELATION,
    SCENARIO_PASS_RATE,
    DURATION,
    ERROR_PATTERN
}

data class ParamSuggestion(
        val param: String,
        val current: String?,
        val suggested: String,
        val reason: String,
        val confidence: SuggestionConfidence = SuggestionConfidence.MEDIUM
)

enum class SuggestionConfidence {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Analyzes test execution reports to surface patterns and suggest param adjustments. Use for
 * what-if analysis and tuning (keywords, coverage threshold, AI temp, etc.).
 */
class TestResultAnalyzer {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** Run analysis on the given reports; returns findings and param suggestions. */
    fun analyze(reports: List<TestExecutionReport>): TestAnalysisResult {
        if (reports.isEmpty()) {
            return TestAnalysisResult(emptyList(), emptyList(), "No reports to analyze.")
        }

        val findings = mutableListOf<AnalysisFinding>()
        val suggestions = mutableListOf<ParamSuggestion>()

        val failed = reports.filter { !it.passed }
        val passed = reports.filter { it.passed }
        val withVerification = reports.filter { it.verification != null }

        // --- Keyword coverage ---
        val failedWithCov =
                failed.mapNotNull { r -> r.verification?.keywordCoverage?.let { r to it } }
        if (failedWithCov.isNotEmpty()) {
            val avgCov = failedWithCov.map { it.second }.average()
            val thresholds =
                    failedWithCov
                            .mapNotNull { (r, _) -> r.verification?.requiredKeywordCoverage }
                            .distinct()
            val typicalThreshold = thresholds.singleOrNull() ?: 0.6
            if (avgCov >= typicalThreshold - 0.15 && avgCov < typicalThreshold) {
                findings.add(
                        AnalysisFinding(
                                kind = FindingKind.KEYWORD_COVERAGE,
                                message =
                                        "Failed tests often have keyword coverage just below threshold.",
                                detail =
                                        "Avg coverage in failures: ${(avgCov * 100).toInt()}%, threshold: ${(typicalThreshold * 100).toInt()}%.",
                                affectedTests = failedWithCov.size
                        )
                )
                suggestions.add(
                        ParamSuggestion(
                                param = "required_keyword_coverage",
                                current = typicalThreshold.toString(),
                                suggested = (typicalThreshold - 0.1).coerceIn(0.3, 0.9).toString(),
                                reason =
                                        "Lowering slightly may reduce false negatives when root cause is correct but wording differs.",
                                confidence = SuggestionConfidence.MEDIUM
                        )
                )
            }
        }

        // --- Missing keywords ---
        val allMissing = failed.flatMap { r -> r.verification?.keywordsMissing.orEmpty() }
        if (allMissing.isNotEmpty()) {
            val counts = allMissing.groupingBy { it.lowercase() }.eachCount()
            val top = counts.entries.sortedByDescending { it.value }.take(5).map { it.key }
            findings.add(
                    AnalysisFinding(
                            kind = FindingKind.MISSING_KEYWORDS,
                            message = "Certain keywords are often missing in failed verifications.",
                            detail =
                                    "Consider adding or accepting synonyms: ${top.joinToString(", ")}.",
                            affectedTests = failed.size
                    )
            )
            suggestions.add(
                    ParamSuggestion(
                            param = "expected_keywords",
                            current = null,
                            suggested = "Add or allow synonyms: ${top.take(3).joinToString(", ")}",
                            reason =
                                    "These terms appear in actual root cause but not in expected keywords.",
                            confidence = SuggestionConfidence.HIGH
                    )
            )
        }

        // --- Component / cause-type mismatch ---
        val failComp = failed.count { r -> r.verification?.componentIdentified == false }
        val failCause = failed.count { r -> r.verification?.causeTypeIdentified == false }
        if (failComp > 0 || failCause > 0) {
            findings.add(
                    AnalysisFinding(
                            kind = FindingKind.COMPONENT_CAUSE_MISMATCH,
                            message =
                                    "Some failures due to component or cause-type not identified.",
                            detail =
                                    "component_identified=false: $failComp, cause_type_identified=false: $failCause. Review expected_component / expected_cause_type vs actual root cause phrasing.",
                            affectedTests = maxOf(failComp, failCause)
                    )
            )
            suggestions.add(
                    ParamSuggestion(
                            param = "expected_component / expected_cause_type",
                            current = null,
                            suggested =
                                    "Align expected labels with LLM wording, or relax matching rules.",
                            reason =
                                    "LLM output may use different terms than scenario expectations.",
                            confidence = SuggestionConfidence.MEDIUM
                    )
            )
        }

        // --- Temperature correlation (when we have it) ---
        val withTemp =
                reports.mapNotNull { r ->
                    val t =
                            (r.metadata["ai_params"] as? Map<*, *>)?.get("temperature")?.let {
                                (it as? Number)?.toDouble()
                            }
                                    ?: (r.metadata["ai_temperature"] as? Number)?.toDouble()
                    if (t != null) r to t else null
                }
        if (withTemp.size >= 3) {
            val failTemps = withTemp.filter { !it.first.passed }.map { it.second }
            val passTemps = withTemp.filter { it.first.passed }.map { it.second }
            if (failTemps.isNotEmpty() && passTemps.isNotEmpty()) {
                val avgFailTemp = failTemps.average()
                val avgPassTemp = passTemps.average()
                if (avgFailTemp > avgPassTemp + 0.1) {
                    findings.add(
                            AnalysisFinding(
                                    kind = FindingKind.TEMPERATURE_CORRELATION,
                                    message = "Failures correlate with higher AI temperature.",
                                    detail =
                                            "Avg temperature in failures: ${String.format("%.2f", avgFailTemp)} vs passes: ${String.format("%.2f", avgPassTemp)}.",
                                    affectedTests = failTemps.size
                            )
                    )
                    suggestions.add(
                            ParamSuggestion(
                                    param = "ai_temperature",
                                    current = String.format("%.2f", avgFailTemp),
                                    suggested = "0.0–0.2",
                                    reason =
                                            "Lower temperature tends to reduce variability and improve verification consistency.",
                                    confidence = SuggestionConfidence.MEDIUM
                            )
                    )
                }
            }
        }

        // --- Scenario pass rate ---
        val byScenario =
                reports
                        .map { r -> r.metadata["scenario_id"]?.toString() ?: r.testName }
                        .distinct()
                        .map { sid ->
                            val group =
                                    reports.filter {
                                        (it.metadata["scenario_id"]?.toString()
                                                ?: it.testName) == sid
                                    }
                            sid to (group.count { it.passed }.toDouble() / group.size)
                        }
        val lowPass = byScenario.filter { it.second < 0.5 && it.second > 0.0 }
        if (lowPass.isNotEmpty()) {
            lowPass.forEach { (sid, rate) ->
                findings.add(
                        AnalysisFinding(
                                kind = FindingKind.SCENARIO_PASS_RATE,
                                message = "Scenario has low pass rate.",
                                detail = "Pass rate: ${(rate * 100).toInt()}%.",
                                affectedTests =
                                        reports.count {
                                            (it.metadata["scenario_id"]?.toString()
                                                    ?: it.testName) == sid
                                        },
                                scenarioId = sid
                        )
                )
            }
            suggestions.add(
                    ParamSuggestion(
                            param = "scenario",
                            current = null,
                            suggested =
                                    "Review expected keywords and root cause for: ${lowPass.map { it.first }.take(3).joinToString(", ")}",
                            reason =
                                    "Low pass rate often due to keyword mismatch or overly strict expected_component/cause_type.",
                            confidence = SuggestionConfidence.HIGH
                    )
            )
        }

        // --- Duration ---
        val analysisDurations =
                reports.mapNotNull { it.analysis?.analysisDurationMs }.filter { it > 0 }
        if (analysisDurations.isNotEmpty()) {
            val p95 = analysisDurations.sorted().let { it[((it.size - 1) * 0.95).toInt()] }
            if (p95 > 30_000) {
                findings.add(
                        AnalysisFinding(
                                kind = FindingKind.DURATION,
                                message = "Analysis duration is high (p95 > 30s).",
                                detail =
                                        "p95: ${p95 / 1000}s. Consider simplifying prior knowledge or prompts.",
                                affectedTests = analysisDurations.size
                        )
                )
                suggestions.add(
                        ParamSuggestion(
                                param = "prior_knowledge / prompts",
                                current = null,
                                suggested =
                                        "Reduce prior knowledge size or simplify prompts to speed up LLM calls.",
                                reason =
                                        "Long analysis duration may slow feedback loops during tuning.",
                                confidence = SuggestionConfidence.LOW
                        )
                )
            }
        }

        // --- Errors (non-verification failures) ---
        val errors = reports.filter { it.status == TestStatus.ERROR }
        if (errors.isNotEmpty()) {
            val messages = errors.mapNotNull { it.error?.take(80) }.distinct().take(3)
            findings.add(
                    AnalysisFinding(
                            kind = FindingKind.ERROR_PATTERN,
                            message = "Some tests ended in ERROR (not verification failure).",
                            detail = "Sample: ${messages.joinToString("; ")}.",
                            affectedTests = errors.size
                    )
            )
        }

        val summary = buildSummary(reports, findings, suggestions)
        logger.info(
                "Analysis complete: ${findings.size} findings, ${suggestions.size} suggestions."
        )
        return TestAnalysisResult(findings, suggestions, summary)
    }

    private fun buildSummary(
            reports: List<TestExecutionReport>,
            findings: List<AnalysisFinding>,
            suggestions: List<ParamSuggestion>
    ): String {
        val total = reports.size
        val passed = reports.count { it.passed }
        val failed = reports.count { it.failed }
        val err = reports.count { it.status == TestStatus.ERROR }
        return buildString {
            appendLine("Analysis summary")
            appendLine("==============")
            appendLine("Tests: $total total, $passed passed, $failed failed, $err errors.")
            appendLine("Findings: ${findings.size}. Suggestions: ${suggestions.size}.")
            if (suggestions.isNotEmpty()) {
                appendLine()
                appendLine("Suggested param adjustments:")
                suggestions.forEach { s ->
                    appendLine("  - ${s.param}: ${s.suggested} (${s.reason.take(80)}...)")
                }
            }
        }
    }

    /** Format analysis as markdown for reports or API responses. */
    fun formatAsMarkdown(result: TestAnalysisResult): String {
        return buildString {
            appendLine("# Test Result Analysis")
            appendLine()
            appendLine(result.summary)
            appendLine()
            if (result.findings.isNotEmpty()) {
                appendLine("## Findings")
                appendLine()
                result.findings.forEach { f ->
                    appendLine("- **${f.kind}**: ${f.message}")
                    if (f.detail != null) appendLine("  - ${f.detail}")
                    if (f.scenarioId != null) appendLine("  - Scenario: ${f.scenarioId}")
                    appendLine()
                }
            }
            if (result.suggestions.isNotEmpty()) {
                appendLine("## Param adjustment suggestions")
                appendLine()
                appendLine("| Param | Current | Suggested | Confidence |")
                appendLine("|-------|---------|-----------|------------|")
                result.suggestions.forEach { s ->
                    appendLine(
                            "| ${s.param} | ${s.current ?: "—"} | ${s.suggested} | ${s.confidence} |"
                    )
                }
                appendLine()
                result.suggestions.forEach { s -> appendLine("- **${s.param}**: ${s.reason}") }
            }
        }
    }
}
