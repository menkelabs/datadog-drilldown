package com.example.rca.dice

import java.io.File
import java.time.Instant
import org.slf4j.LoggerFactory

/**
 * Collects test execution reports and generates reports at the end of test runs. Also persists each
 * report to H2 (see [H2TestReportStore]) for lookup and analysis via the test-report-server.
 * Persistence failures are logged but do not fail tests.
 *
 * Usage:
 * ```kotlin
 * val collector = TestReportCollector()
 *
 * @AfterAll
 * fun generateReports() {
 *     collector.generateReports("test-reports")
 * }
 * ```
 */
class TestReportCollector(private val h2Store: H2TestReportStore? = H2TestReportStore()) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val reports = mutableListOf<TestExecutionReport>()

    /** Add a test execution report. Persists to H2 when store is configured. */
    fun addReport(report: TestExecutionReport) {
        reports.add(report)
        logger.debug("Added report for test: ${report.testName}")
        h2Store?.let { store ->
            try {
                store.addReport(report)
            } catch (e: Exception) {
                logger.warn("Failed to persist report to H2: ${report.testName} — ${e.message}")
            }
        }
    }

    /** Get all collected reports. */
    fun getReports(): List<TestExecutionReport> = reports.toList()

    /** Clear all reports. */
    fun clear() {
        reports.clear()
    }

    /** Generate all report formats in the specified directory. */
    fun generateReports(outputDir: String = "test-reports") {
        val dir = File(outputDir)
        dir.mkdirs()

        val generator = TestReportGenerator()
        val timestamp =
                Instant.now()
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val runId = System.getenv("TEST_RUN_ID") ?: "run-${Instant.now().epochSecond}"

        // Generate JSON report
        val jsonFile = File(dir, "test-report-$timestamp.json")
        generator.generateJsonReport(reports, jsonFile)
        logger.info("Generated JSON report: ${jsonFile.absolutePath}")

        // Generate Markdown report
        val mdFile = File(dir, "test-report-$timestamp.md")
        generator.generateMarkdownReport(reports, mdFile)
        logger.info("Generated Markdown report: ${mdFile.absolutePath}")

        // Generate HTML report
        val htmlFile = File(dir, "test-report-$timestamp.html")
        generator.generateHtmlReport(reports, htmlFile)
        logger.info("Generated HTML report: ${htmlFile.absolutePath}")

        // Run analysis and write suggestions (include per-test actual LLM output vs expected).
        // Use {runId}-analysis.md so the key matches runs/logs; runId is the base, -analysis the type.
        val analyzer = TestResultAnalyzer()
        val analysisResult = analyzer.analyze(reports)
        val analysisFile = File(dir, "$runId-analysis.md")
        analysisFile.writeText(analyzer.formatAsMarkdown(analysisResult, reports))
        logger.info(
                "Generated analysis: ${analysisFile.absolutePath} (${analysisResult.suggestions.size} suggestions)"
        )

        // Generate summary
        val summary = buildString {
            appendLine("Test Report Generation Complete")
            appendLine("==============================")
            appendLine("Total tests: ${reports.size}")
            appendLine("Passed: ${reports.count { it.passed }}")
            appendLine("Failed: ${reports.count { it.failed }}")
            appendLine("Errors: ${reports.count { it.status == TestStatus.ERROR }}")
            appendLine()
            appendLine("Reports generated in: ${dir.absolutePath}")
            appendLine("- JSON: ${jsonFile.name}")
            appendLine("- Markdown: ${mdFile.name}")
            appendLine("- HTML: ${htmlFile.name}")
            appendLine("- Analysis: ${analysisFile.name}")
        }

        logger.info(summary)
    }

    /** Run analysis on collected reports and return findings + param suggestions. */
    fun runAnalysis(): TestAnalysisResult {
        val analyzer = TestResultAnalyzer()
        return analyzer.analyze(reports)
    }

    /**
     * Generate a quick summary report. Includes analysis and param-adjustment suggestions when
     * available.
     */
    fun generateSummary(outputFile: File) {
        val passed = reports.count { it.passed }
        val failed = reports.count { it.failed }
        val errors = reports.count { it.status == TestStatus.ERROR }
        val total = reports.size

        val summary = buildString {
            appendLine("Test Execution Summary")
            appendLine("=====================")
            appendLine()
            appendLine("Total: $total | Passed: $passed | Failed: $failed | Errors: $errors")
            appendLine()

            if (failed > 0 || errors > 0) {
                appendLine("Failed/Error Tests:")
                reports.filter { it.failed || it.status == TestStatus.ERROR }.forEach { report ->
                    appendLine("- ${report.testName}: ${report.error ?: "Verification failed"}")
                }
                appendLine()
            }

            if (reports.isNotEmpty()) {
                val analysisResult = TestResultAnalyzer().analyze(reports)
                if (analysisResult.hasSuggestions()) {
                    appendLine("Suggested param adjustments:")
                    analysisResult.suggestions.forEach { s ->
                        appendLine("- ${s.param}: ${s.suggested}")
                        appendLine(
                                "  Reason: ${s.reason.take(150)}${if (s.reason.length > 150) "…" else ""}"
                        )
                    }
                    appendLine()
                    appendLine("See run-*-analysis.md (or analysis-suggestions-*.md) in test-reports for full analysis.")
                }
            }
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(summary)
    }
}
