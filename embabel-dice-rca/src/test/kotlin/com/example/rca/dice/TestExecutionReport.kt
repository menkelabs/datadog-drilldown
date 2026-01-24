package com.example.rca.dice

import java.time.Duration
import java.time.Instant

/**
 * Comprehensive test execution report that captures all information during test runs. This can be
 * serialized to JSON, Markdown, or HTML for human-readable reports.
 */
data class TestExecutionReport(
        val testName: String,
        val testClass: String,
        val startTime: Instant,
        val endTime: Instant? = null,
        val duration: Duration? = null,
        val status: TestStatus,
        val error: String? = null,
        val stackTrace: String? = null,

        // Test setup information
        val contextId: String,
        val diceServerUrl: String,

        // Prior knowledge loading
        val priorKnowledge: PriorKnowledgeInfo? = null,

        // Alert simulation
        val alert: AlertInfo? = null,

        // Analysis results
        val analysis: AnalysisInfo? = null,

        // Verification results
        val verification: VerificationInfo? = null,

        // Performance metrics
        val performance: PerformanceMetrics? = null,

        // Additional metadata
        val metadata: Map<String, Any> = emptyMap()
) {
    val durationMs: Long? = duration?.toMillis()
    val passed: Boolean = status == TestStatus.PASSED
    val failed: Boolean = status == TestStatus.FAILED
}

enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}

data class PriorKnowledgeInfo(
        val loadResult: LoadResult,
        val architecture: SystemArchitecture? = null,
        val dependenciesCount: Int = 0,
        val failurePatternsCount: Int = 0,
        val pastIncidentsCount: Int = 0,
        val runbooksCount: Int = 0,
        val slosCount: Int = 0,
        val loadDurationMs: Long? = null
)

data class AlertInfo(
        val id: String,
        val name: String,
        val timestamp: Instant,
        val severity: String,
        val service: String? = null,
        val message: String,
        val metricsQueried: List<String> = emptyList(),
        val logQueries: List<String> = emptyList(),
        val traceQueries: List<Pair<String, String>> = emptyList()
)

data class AnalysisInfo(
        val alertId: String,
        val initialAssessment: String,
        val relevantPatterns: List<PatternInfo> = emptyList(),
        val evidenceGathered: List<EvidenceInfo> = emptyList(),
        val rootCauseAnalysis: String,
        val recommendations: String,
        val propositions: List<PropositionInfo> = emptyList(),
        val analysisDurationMs: Long? = null
)

data class PatternInfo(val id: String? = null, val text: String, val confidence: Double? = null)

data class EvidenceInfo(
        val source: String,
        val type: String, // metric, log, trace, event, monitor
        val summary: String,
        val itemCount: Int = 0
)

data class PropositionInfo(
        val id: String,
        val text: String,
        val confidence: Double,
        val reasoning: String? = null
)

data class VerificationInfo(
        val passed: Boolean,
        val keywordsFound: List<String>,
        val keywordsMissing: List<String>,
        val keywordCoverage: Double,
        val componentIdentified: Boolean,
        val causeTypeIdentified: Boolean,
        val expectedKeywords: List<String>,
        val expectedComponent: String? = null,
        val expectedCauseType: String? = null,
        val actualRootCause: String,
        val requiredKeywordCoverage: Double = 0.6
) {
    val keywordCoveragePercent: Int = (keywordCoverage * 100).toInt()
}

data class PerformanceMetrics(
        val priorKnowledgeLoadMs: Long? = null,
        val alertIngestionMs: Long? = null,
        val initialAssessmentMs: Long? = null,
        val evidenceGatheringMs: Long? = null,
        val rootCauseAnalysisMs: Long? = null,
        val recommendationsMs: Long? = null,
        val totalAnalysisMs: Long? = null,
        val totalTestMs: Long? = null,
        val diceApiCalls: Int = 0,
        val datadogApiCalls: Int = 0
)

/** Builder for creating test execution reports incrementally. */
class TestExecutionReportBuilder(
        val testName: String,
        val testClass: String,
        val contextId: String,
        val diceServerUrl: String
) {
    private val startTime = Instant.now()
    private var endTime: Instant? = null
    private var status: TestStatus = TestStatus.PASSED
    private var error: String? = null
    private var stackTrace: String? = null

    private var priorKnowledge: PriorKnowledgeInfo? = null
    private var alert: AlertInfo? = null
    private var analysis: AnalysisInfo? = null
    private var verification: VerificationInfo? = null
    private var performance: PerformanceMetrics? = null
    private val metadata = mutableMapOf<String, Any>()

    fun withPriorKnowledge(
            loadResult: LoadResult,
            architecture: SystemArchitecture? = null,
            dependenciesCount: Int = 0,
            failurePatternsCount: Int = 0,
            pastIncidentsCount: Int = 0,
            runbooksCount: Int = 0,
            slosCount: Int = 0,
            loadDurationMs: Long? = null
    ): TestExecutionReportBuilder {
        this.priorKnowledge =
                PriorKnowledgeInfo(
                        loadResult = loadResult,
                        architecture = architecture,
                        dependenciesCount = dependenciesCount,
                        failurePatternsCount = failurePatternsCount,
                        pastIncidentsCount = pastIncidentsCount,
                        runbooksCount = runbooksCount,
                        slosCount = slosCount,
                        loadDurationMs = loadDurationMs
                )
        return this
    }

    fun withAlert(alert: AlertInfo): TestExecutionReportBuilder {
        this.alert = alert
        return this
    }

    fun withAnalysis(
            analysis: AnalysisResult,
            analysisDurationMs: Long? = null
    ): TestExecutionReportBuilder {
        this.analysis =
                AnalysisInfo(
                        alertId = analysis.alertId,
                        initialAssessment = analysis.initialAssessment,
                        relevantPatterns =
                                analysis.relevantPatterns.map {
                                    PatternInfo(
                                            id = it.id,
                                            text = it.text,
                                            confidence = it.confidence
                                    )
                                },
                        evidenceGathered =
                                analysis.evidenceGathered.map { source ->
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
                        rootCauseAnalysis = analysis.rootCauseAnalysis,
                        recommendations = analysis.recommendations,
                        propositions =
                                analysis.propositions.map {
                                    PropositionInfo(
                                            id = it.id,
                                            text = it.text,
                                            confidence = it.confidence,
                                            reasoning = it.reasoning
                                    )
                                },
                        analysisDurationMs = analysisDurationMs
                )
        return this
    }

    fun withVerification(
            verification: VerificationResult,
            expectedRootCause: ExpectedRootCause
    ): TestExecutionReportBuilder {
        this.verification =
                VerificationInfo(
                        passed = verification.passed,
                        keywordsFound = verification.keywordsFound,
                        keywordsMissing = verification.keywordsMissing,
                        keywordCoverage = verification.keywordCoverage,
                        componentIdentified = verification.componentIdentified,
                        causeTypeIdentified = verification.causeTypeIdentified,
                        expectedKeywords = verification.expectedKeywords,
                        expectedComponent = expectedRootCause.component,
                        expectedCauseType = expectedRootCause.causeType,
                        actualRootCause = verification.actualRootCause,
                        requiredKeywordCoverage = expectedRootCause.requiredKeywordCoverage
                )
        if (!verification.passed) {
            this.status = TestStatus.FAILED
        }
        return this
    }

    fun withPerformance(performance: PerformanceMetrics): TestExecutionReportBuilder {
        this.performance = performance
        return this
    }

    fun withMetadata(key: String, value: Any): TestExecutionReportBuilder {
        this.metadata[key] = value
        return this
    }

    fun markFailed(error: String, stackTrace: String? = null): TestExecutionReportBuilder {
        this.status = TestStatus.FAILED
        this.error = error
        this.stackTrace = stackTrace
        return this
    }

    fun markError(error: String, stackTrace: String? = null): TestExecutionReportBuilder {
        this.status = TestStatus.ERROR
        this.error = error
        this.stackTrace = stackTrace
        return this
    }

    fun build(): TestExecutionReport {
        endTime = Instant.now()
        val duration = if (endTime != null) Duration.between(startTime, endTime) else null

        return TestExecutionReport(
                testName = testName,
                testClass = testClass,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                status = status,
                error = error,
                stackTrace = stackTrace,
                contextId = contextId,
                diceServerUrl = diceServerUrl,
                priorKnowledge = priorKnowledge,
                alert = alert,
                analysis = analysis,
                verification = verification,
                performance = performance,
                metadata = metadata.toMap()
        )
    }
}
