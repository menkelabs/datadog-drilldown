package com.example.rca.dice

import org.slf4j.LoggerFactory

/** Extension functions to simplify test reporting in test classes. */

/**
 * Global test report collector instance. Tests can add reports to this, and it will be used to
 * generate final reports.
 */
val globalTestReportCollector = TestReportCollector()

/** Extension function to create a test report builder with common setup. */
fun DiceKnowledgeTestFramework.createTestReport(
        testName: String,
        testClass: String,
        contextId: String,
        diceServerUrl: String = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"
): TestExecutionReportBuilder {
    return TestExecutionReportBuilder(testName, testClass, contextId, diceServerUrl)
}

/**
 * Helper function to run a test with automatic report collection.
 *
 * Example usage:
 * ```kotlin
 * @Test
 * fun `my test`() {
 *     runTestWithReporting(
 *         testName = "my test",
 *         contextId = "test-${System.currentTimeMillis()}"
 *     ) { reportBuilder ->
 *         // Load prior knowledge
 *         val loadResult = testFramework.loadPriorKnowledge(...)
 *         reportBuilder.withPriorKnowledge(loadResult, ...)
 *
 *         // Simulate alert
 *         val alert = TestAlert(...)
 *         val analysisResult = testFramework.simulateAlert(contextId, alert)
 *         reportBuilder.withAlert(AlertInfo(...))
 *         reportBuilder.withAnalysis(analysisResult)
 *
 *         // Verify
 *         val verification = testFramework.verifyConclusion(analysisResult, expectedRootCause)
 *         reportBuilder.withVerification(verification, expectedRootCause)
 *
 *         // Assert
 *         assertTrue(verification.passed)
 *     }
 * }
 * ```
 */
inline fun <T> runTestWithReporting(
        testName: String,
        testClass: String = run {
            // Find the actual test class by walking up the stack
            val stack = Thread.currentThread().stackTrace
            stack.firstOrNull { 
                it.className.contains("Test") && 
                !it.className.contains("jdk.internal") && 
                !it.className.contains("kotlin.test") &&
                !it.className.contains("junit")
            }?.className ?: stack[2].className
        },
        contextId: String,
        diceServerUrl: String = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080",
        block: (TestExecutionReportBuilder) -> T
): T {
    val logger = LoggerFactory.getLogger(testClass)
    val reportBuilder = TestExecutionReportBuilder(testName, testClass, contextId, diceServerUrl)

    return try {
        val result = block(reportBuilder)
        val report = reportBuilder.build()
        globalTestReportCollector.addReport(report)
        logger.info("Test completed: $testName - ${report.status}")
        result
    } catch (e: AssertionError) {
        val report =
                reportBuilder
                        .markFailed(e.message ?: "Assertion failed", e.stackTraceToString())
                        .build()
        globalTestReportCollector.addReport(report)
        logger.error("Test failed: $testName", e)
        throw e
    } catch (e: Exception) {
        val report =
                reportBuilder
                        .markError(e.message ?: "Unexpected error", e.stackTraceToString())
                        .build()
        globalTestReportCollector.addReport(report)
        logger.error("Test error: $testName", e)
        throw e
    }
}

/** Helper to convert AnalysisResult to report format with timing. */
fun AnalysisResult.toReportInfo(analysisDurationMs: Long? = null): AnalysisInfo {
    return AnalysisInfo(
            alertId = this.alertId,
            initialAssessment = this.initialAssessment,
            relevantPatterns =
                    this.relevantPatterns.map {
                        PatternInfo(id = it.id, text = it.text, confidence = it.confidence)
                    },
            evidenceGathered =
                    this.evidenceGathered.map { source ->
                        EvidenceInfo(
                                source = source,
                                type =
                                        when {
                                            source.startsWith("metric-") -> "metric"
                                            source.startsWith("logs-") -> "log"
                                            source.startsWith("traces-") -> "trace"
                                            source == "events" -> "event"
                                            source.startsWith("monitor-") -> "monitor"
                                            else -> "unknown"
                                        },
                                summary = source,
                                itemCount = 0
                        )
                    },
            rootCauseAnalysis = this.rootCauseAnalysis,
            recommendations = this.recommendations,
            propositions =
                    this.propositions.map {
                        PropositionInfo(
                                id = it.id,
                                text = it.text,
                                confidence = it.confidence,
                                reasoning = it.reasoning
                        )
                    },
            analysisDurationMs = analysisDurationMs
    )
}

/** Helper to convert TestAlert to AlertInfo. */
fun TestAlert.toAlertInfo(): AlertInfo {
    return AlertInfo(
            id = this.id,
            name = this.name,
            timestamp = this.timestamp,
            severity = this.severity,
            service = this.service,
            message = this.message,
            metricsQueried = this.metricsToQuery,
            logQueries = this.logQueries,
            traceQueries = this.traceQueries
    )
}
