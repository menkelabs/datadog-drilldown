package com.example.rca.dice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Generates human-readable reports from test execution data. Supports JSON, Markdown, and HTML
 * formats.
 */
class TestReportGenerator {
    private val objectMapper: ObjectMapper =
            jacksonObjectMapper()
                    .registerModule(JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Generate a JSON report. */
    fun generateJsonReport(reports: List<TestExecutionReport>, outputFile: File) {
        val json = objectMapper.writeValueAsString(reports)
        outputFile.writeText(json)
    }

    /** Generate a Markdown report. */
    fun generateMarkdownReport(reports: List<TestExecutionReport>, outputFile: File) {
        val markdown = buildString {
            appendLine("# Test Execution Report")
            appendLine()
            appendLine(
                    "Generated: ${java.time.Instant.now().atZone(java.time.ZoneId.systemDefault()).format(dateFormatter)}"
            )
            appendLine()
            appendLine("## Summary")
            appendLine()
            val passed = reports.count { it.passed }
            val failed = reports.count { it.failed }
            val errors = reports.count { it.status == TestStatus.ERROR }
            val total = reports.size
            val totalDuration = reports.mapNotNull { it.durationMs }.sum()

            appendLine("| Metric | Count |")
            appendLine("|--------|-------|")
            appendLine("| Total Tests | $total |")
            appendLine("| Passed | $passed |")
            appendLine("| Failed | $failed |")
            appendLine("| Errors | $errors |")
            appendLine("| Total Duration | ${totalDuration}ms (${totalDuration / 1000.0}s) |")
            appendLine()

            appendLine("## Test Results")
            appendLine()

            reports.forEach { report ->
                val statusIcon =
                        when (report.status) {
                            TestStatus.PASSED -> "✅"
                            TestStatus.FAILED -> "❌"
                            TestStatus.ERROR -> "⚠️"
                            TestStatus.SKIPPED -> "⏭️"
                        }

                appendLine("### $statusIcon ${report.testName}")
                appendLine()
                appendLine("**Class:** `${report.testClass}`")
                appendLine("**Status:** ${report.status}")
                appendLine("**Duration:** ${report.durationMs ?: "N/A"}ms")
                appendLine("**Context ID:** `${report.contextId}`")
                appendLine()

                // Prior Knowledge
                report.priorKnowledge?.let { pk ->
                    appendLine("#### Prior Knowledge")
                    appendLine(
                            "- Documents loaded: ${pk.loadResult.documentsLoaded}/${pk.loadResult.totalDocuments}"
                    )
                    appendLine("- Propositions extracted: ${pk.loadResult.propositionsExtracted}")
                    appendLine("- Dependencies: ${pk.dependenciesCount}")
                    appendLine("- Failure patterns: ${pk.failurePatternsCount}")
                    appendLine("- Past incidents: ${pk.pastIncidentsCount}")
                    if (pk.loadDurationMs != null) {
                        appendLine("- Load duration: ${pk.loadDurationMs}ms")
                    }
                    appendLine()
                }

                // Alert
                report.alert?.let { alert ->
                    appendLine("#### Alert")
                    appendLine("- **ID:** ${alert.id}")
                    appendLine("- **Name:** ${alert.name}")
                    appendLine("- **Service:** ${alert.service ?: "N/A"}")
                    appendLine("- **Severity:** ${alert.severity}")
                    appendLine("- **Message:** ${alert.message}")
                    appendLine()
                }

                // Analysis
                report.analysis?.let { analysis ->
                    appendLine("#### Analysis")
                    appendLine()
                    appendLine("**Initial Assessment:**")
                    appendLine("```")
                    appendLine(analysis.initialAssessment.take(500))
                    if (analysis.initialAssessment.length > 500) appendLine("...")
                    appendLine("```")
                    appendLine()

                    appendLine("**Root Cause Analysis:**")
                    appendLine("```")
                    appendLine(analysis.rootCauseAnalysis)
                    appendLine("```")
                    appendLine()

                    if (analysis.recommendations.isNotBlank()) {
                        appendLine("**Recommendations:**")
                        appendLine("```")
                        appendLine(analysis.recommendations)
                        appendLine("```")
                        appendLine()
                    }

                    appendLine("- Propositions: ${analysis.propositions.size}")
                    appendLine("- Relevant patterns: ${analysis.relevantPatterns.size}")
                    appendLine("- Evidence sources: ${analysis.evidenceGathered.size}")
                    if (analysis.analysisDurationMs != null) {
                        appendLine("- Analysis duration: ${analysis.analysisDurationMs}ms")
                    }
                    appendLine()
                }

                // Verification
                report.verification?.let { verification ->
                    appendLine("#### Verification")
                    appendLine()
                    val verificationStatus = if (verification.passed) "✅ PASSED" else "❌ FAILED"
                    appendLine("**Status:** $verificationStatus")
                    appendLine()
                    appendLine(
                            "**Expected Keywords:** ${verification.expectedKeywords.joinToString(", ")}"
                    )
                    appendLine(
                            "**Keywords Found:** ${verification.keywordsFound.joinToString(", ")}"
                    )
                    appendLine(
                            "**Keywords Missing:** ${verification.keywordsMissing.joinToString(", ")}"
                    )
                    appendLine(
                            "**Keyword Coverage:** ${verification.keywordCoveragePercent}% (required: ${(verification.requiredKeywordCoverage * 100).toInt()}%)"
                    )
                    appendLine(
                            "**Component Identified:** ${if (verification.componentIdentified) "✅" else "❌"}"
                    )
                    if (verification.expectedComponent != null) {
                        appendLine("**Expected Component:** ${verification.expectedComponent}")
                    }
                    appendLine()
                }

                // Performance
                report.performance?.let { perf ->
                    appendLine("#### Performance Metrics")
                    appendLine()
                    if (perf.totalTestMs != null) {
                        appendLine("- Total test time: ${perf.totalTestMs}ms")
                    }
                    if (perf.totalAnalysisMs != null) {
                        appendLine("- Total analysis time: ${perf.totalAnalysisMs}ms")
                    }
                    appendLine("- DICE API calls: ${perf.diceApiCalls}")
                    appendLine("- Datadog API calls: ${perf.datadogApiCalls}")
                    appendLine()
                }

                // Error details
                report.error?.let { error ->
                    appendLine("#### Error Details")
                    appendLine("```")
                    appendLine(error)
                    appendLine("```")
                    appendLine()
                }

                appendLine("---")
                appendLine()
            }
        }

        outputFile.writeText(markdown)
    }

    /** Generate an HTML report. */
    fun generateHtmlReport(reports: List<TestExecutionReport>, outputFile: File) {
        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("<title>Test Execution Report</title>")
            appendLine("<style>")
            appendLine(
                    """
                body { font-family: Arial, sans-serif; margin: 20px; }
                .summary { background: #f5f5f5; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
                .test { border: 1px solid #ddd; margin: 10px 0; padding: 15px; border-radius: 5px; }
                .passed { border-left: 5px solid #4CAF50; }
                .failed { border-left: 5px solid #f44336; }
                .error { border-left: 5px solid #ff9800; }
                .code { background: #f4f4f4; padding: 10px; border-radius: 3px; font-family: monospace; white-space: pre-wrap; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #4CAF50; color: white; }
            """.trimIndent()
            )
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<h1>Test Execution Report</h1>")

            val passed = reports.count { it.passed }
            val failed = reports.count { it.failed }
            val errors = reports.count { it.status == TestStatus.ERROR }
            val total = reports.size

            appendLine("<div class='summary'>")
            appendLine("<h2>Summary</h2>")
            appendLine("<table>")
            appendLine("<tr><th>Metric</th><th>Count</th></tr>")
            appendLine("<tr><td>Total Tests</td><td>$total</td></tr>")
            appendLine("<tr><td>Passed</td><td style='color: green;'>$passed</td></tr>")
            appendLine("<tr><td>Failed</td><td style='color: red;'>$failed</td></tr>")
            appendLine("<tr><td>Errors</td><td style='color: orange;'>$errors</td></tr>")
            appendLine("</table>")
            appendLine("</div>")

            appendLine("<h2>Test Results</h2>")

            reports.forEach { report ->
                val statusClass =
                        when (report.status) {
                            TestStatus.PASSED -> "passed"
                            TestStatus.FAILED -> "failed"
                            TestStatus.ERROR -> "error"
                            TestStatus.SKIPPED -> "test"
                        }

                appendLine("<div class='test $statusClass'>")
                appendLine("<h3>${report.testName}</h3>")
                appendLine("<p><strong>Status:</strong> ${report.status}</p>")
                appendLine("<p><strong>Duration:</strong> ${report.durationMs ?: "N/A"}ms</p>")

                report.verification?.let { verification ->
                    appendLine("<h4>Verification</h4>")
                    appendLine(
                            "<p><strong>Result:</strong> ${if (verification.passed) "✅ PASSED" else "❌ FAILED"}</p>"
                    )
                    appendLine(
                            "<p><strong>Keyword Coverage:</strong> ${verification.keywordCoveragePercent}%</p>"
                    )
                    appendLine(
                            "<p><strong>Keywords Found:</strong> ${verification.keywordsFound.joinToString(", ")}</p>"
                    )
                    appendLine(
                            "<p><strong>Keywords Missing:</strong> ${verification.keywordsMissing.joinToString(", ")}</p>"
                    )
                }

                report.analysis?.let { analysis ->
                    appendLine("<h4>Root Cause Analysis</h4>")
                    appendLine("<div class='code'>${analysis.rootCauseAnalysis.escapeHtml()}</div>")
                }

                report.error?.let { error ->
                    appendLine("<h4>Error</h4>")
                    appendLine("<div class='code'>${error.escapeHtml()}</div>")
                }

                appendLine("</div>")
            }

            appendLine("</body>")
            appendLine("</html>")
        }

        outputFile.writeText(html)
    }

    private fun String.escapeHtml(): String {
        return this.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
    }
}
