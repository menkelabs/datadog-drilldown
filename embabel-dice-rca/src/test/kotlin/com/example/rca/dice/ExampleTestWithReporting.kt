package com.example.rca.dice

import com.example.rca.mock.MockDatadogClient
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Example test class showing how to use the test reporting system.
 *
 * This demonstrates:
 * 1. Using runTestWithReporting for automatic report collection
 * 2. Adding all relevant test data to the report
 * 3. Generating reports after all tests complete
 */
class ExampleTestWithReporting {

    private lateinit var diceClient: DiceClient
    private lateinit var mockDatadog: MockDatadogClient
    private lateinit var testFramework: DiceKnowledgeTestFramework

    @BeforeEach
    fun setup() {
        diceClient = DiceClient(System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080")
        mockDatadog = MockDatadogClient()
        testFramework = DiceKnowledgeTestFramework(diceClient, mockDatadog)
    }

    @Test
    @EnabledIfEnvironmentVariable(
            named = "DICE_SERVER_URL",
            matches = ".*",
            disabledReason = "Set DICE_SERVER_URL env var to run integration test"
    )
    fun `example test with automatic reporting`() {
        val contextId = "example-test-${System.currentTimeMillis()}"

        runTestWithReporting(
                testName = "example test with automatic reporting",
                contextId = contextId
        ) { reportBuilder ->
            // Step 1: Load prior knowledge
            val priorKnowledge =
                    PriorKnowledge(
                            failurePatterns =
                                    listOf(
                                            FailurePattern(
                                                    id = "fp-1",
                                                    name = "Example Failure Pattern",
                                                    description = "An example failure pattern",
                                                    symptoms =
                                                            listOf(
                                                                    "High latency",
                                                                    "Error rate spike"
                                                            ),
                                                    rootCause = "Resource exhaustion",
                                                    resolution = "Scale resources",
                                                    affectedServices = listOf("example-service")
                                            )
                                    ),
                            dependencies =
                                    listOf(
                                            ServiceDependency(
                                                    from = "example-service",
                                                    to = "database",
                                                    type = "database",
                                                    critical = true
                                            )
                                    )
                    )

            val loadStart = System.currentTimeMillis()
            val loadResult = testFramework.loadPriorKnowledge(contextId, priorKnowledge)
            val loadDuration = System.currentTimeMillis() - loadStart

            reportBuilder.withPriorKnowledge(
                    loadResult = loadResult,
                    dependenciesCount = priorKnowledge.dependencies.size,
                    failurePatternsCount = priorKnowledge.failurePatterns.size,
                    loadDurationMs = loadDuration
            )

            // Step 2: Simulate alert
            val alert =
                    TestAlert(
                            name = "Example Alert",
                            service = "example-service",
                            message = "High latency detected",
                            metricsToQuery = listOf("latency{service:example-service}"),
                            logQueries = listOf("service:example-service status:error")
                    )

            val analysisStart = System.currentTimeMillis()
            val analysisResult = testFramework.simulateAlert(contextId, alert, "example-scenario")
            val analysisDuration = System.currentTimeMillis() - analysisStart

            reportBuilder.withAlert(alert.toAlertInfo())
            reportBuilder.withAnalysis(analysisResult, analysisDuration)

            // Step 3: Verify conclusion
            val expectedRootCause =
                    ExpectedRootCause(
                            keywords = listOf("latency", "resource", "exhaustion"),
                            component = "service",
                            requiredKeywordCoverage = 0.6
                    )

            val verification = testFramework.verifyConclusion(analysisResult, expectedRootCause)
            reportBuilder.withVerification(verification, expectedRootCause)

            // Step 4: Performance metrics
            val performance =
                    PerformanceMetrics(
                            priorKnowledgeLoadMs = loadDuration,
                            totalAnalysisMs = analysisDuration,
                            totalTestMs = System.currentTimeMillis() - loadStart,
                            diceApiCalls = 5, // Approximate
                            datadogApiCalls = 2
                    )
            reportBuilder.withPerformance(performance)

            // Step 5: Assert
            assertTrue(verification.passed, "Test should pass verification")
        }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun generateReports() {
            // Generate reports after all tests complete
            // Reports will be in test-reports/ directory
            globalTestReportCollector.generateReports("test-reports")

            // Also generate a quick summary
            val summaryFile = java.io.File("test-reports", "test-summary.txt")
            summaryFile.parentFile?.mkdirs()
            globalTestReportCollector.generateSummary(summaryFile)
        }
    }
}
