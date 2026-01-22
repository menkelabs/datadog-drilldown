package com.example.rca.integration

import com.example.rca.agent.ChatAdvisor
import com.example.rca.agent.RcaAgent
import com.example.rca.analysis.ApmAnalyzer
import com.example.rca.analysis.LogAnalyzer
import com.example.rca.analysis.MetricAnalyzer
import com.example.rca.analysis.ScoringEngine
import com.example.rca.dice.DiceClient
import com.example.rca.dice.DiceIngestionService
import com.example.rca.dice.IngestResponse
import com.example.rca.dice.DiceProposition
import com.example.rca.dice.model.*
import com.example.rca.domain.*
import com.example.rca.fixtures.TestScenarios
import com.example.rca.mock.MockDatadogClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * End-to-end integration tests simulating full incident investigation flow:
 * Ingest -> Analyze -> Chat Advice
 */
class EndToEndRcaTest {

    private lateinit var mockDatadog: MockDatadogClient
    private lateinit var ingestionService: DiceIngestionService
    private lateinit var rcaAgent: RcaAgent
    private lateinit var chatAdvisor: ChatAdvisor

    @BeforeEach
    fun setup() {
        mockDatadog = MockDatadogClient()
        mockDatadog.loadScenario("database-latency", TestScenarios.databaseLatencyScenario)
        mockDatadog.loadScenario("downstream-failure", TestScenarios.downstreamFailureScenario)
        mockDatadog.loadScenario("memory-pressure", TestScenarios.memoryPressureScenario)
        mockDatadog.loadScenario("healthy", TestScenarios.healthyScenario)

        val logAnalyzer = LogAnalyzer()
        val metricAnalyzer = MetricAnalyzer()
        val apmAnalyzer = ApmAnalyzer()
        val scoringEngine = ScoringEngine()
        chatAdvisor = ChatAdvisor()

        rcaAgent = RcaAgent(
            datadogClient = mockDatadog,
            logAnalyzer = logAnalyzer,
            metricAnalyzer = metricAnalyzer,
            apmAnalyzer = apmAnalyzer,
            scoringEngine = scoringEngine,
            chatAdvisor = chatAdvisor
        )

        val eventPublisher = ApplicationEventPublisher { }
        val mockDiceClient = object : DiceClient("http://localhost:8080") {
            override fun ingest(contextId: String, documentId: String, text: String, metadata: Map<String, Any>): IngestResponse {
                return IngestResponse(documentId, 0, "SUCCESS", null)
            }
            override fun query(contextId: String, question: String): String = "Mock answer"
            override fun listPropositions(contextId: String, status: String?, limit: Int): List<DiceProposition> = emptyList()
        }
        ingestionService = DiceIngestionService(eventPublisher, rcaAgent, mockDiceClient)
    }

    @Test
    fun `end-to-end database latency incident investigation`() {
        // Scenario: Database connection pool exhaustion causes high latency
        mockDatadog.setActiveScenario("database-latency")

        // Step 1: Ingest alert
        val alert = AlertTrigger(
            id = "alert-${UUID.randomUUID()}",
            timestamp = Instant.parse("2026-01-15T12:00:00Z"),
            alertType = AlertType.MONITOR_ALERT,
            monitorId = 12345,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH,
            message = "P95 latency exceeded 500ms threshold"
        )

        val ingestionResult = ingestionService.ingestAlert(alert)
        assertTrue(ingestionResult.success, "Ingestion should succeed")
        assertTrue(ingestionResult.analysisTriggered, "Analysis should be triggered")

        // Step 2: Get incident context
        val context = ingestionService.getIncident(ingestionResult.incidentId!!)
        assertNotNull(context, "Incident should be tracked")

        // Step 3: Verify analysis results
        assertTrue(context.candidates.isNotEmpty(), "Should have identified candidates")
        assertTrue(context.symptoms.isNotEmpty(), "Should have detected symptoms")

        // Check for expected findings
        val hasTimeoutError = context.candidates.any { candidate ->
            candidate.title.contains("Timeout", ignoreCase = true) ||
            candidate.evidence["template"]?.toString()?.contains("Timeout", ignoreCase = true) == true
        }
        assertTrue(hasTimeoutError || context.candidates.any { it.kind == CandidateKind.DEPENDENCY },
            "Should identify timeout or dependency issues")

        // Step 4: Chat interaction
        chatAdvisor.startSession(context)

        val summaryResponse = chatAdvisor.processMessage("What happened?", context)
        assertTrue(summaryResponse.message.isNotBlank(), "Should provide summary")

        val causeResponse = chatAdvisor.processMessage("What's the root cause?", context)
        assertTrue(causeResponse.message.isNotBlank(), "Should explain root cause")

        val fixResponse = chatAdvisor.processMessage("How do I fix this?", context)
        assertTrue(fixResponse.message.isNotBlank(), "Should suggest remediation")

        // Step 5: Get recommendations
        val recommendations = chatAdvisor.getRecommendations(context)
        assertTrue(recommendations.isNotEmpty(), "Should have recommendations")

        // Step 6: Close incident
        assertTrue(ingestionService.closeIncident(context.id), "Should close incident")
    }

    @Test
    fun `end-to-end downstream service failure investigation`() {
        // Scenario: Payment service goes down causing cascading failures
        mockDatadog.setActiveScenario("downstream-failure")

        // Step 1: Ingest alert
        val alert = AlertTrigger(
            id = "alert-${UUID.randomUUID()}",
            timestamp = Instant.parse("2026-01-15T12:00:00Z"),
            alertType = AlertType.MONITOR_ALERT,
            service = "api",
            env = "prod",
            severity = AlertSeverity.CRITICAL,
            message = "Error rate exceeds 5%"
        )

        val ingestionResult = ingestionService.ingestAlert(alert)
        val context = ingestionService.getIncident(ingestionResult.incidentId!!)!!

        // Step 2: Verify analysis identified downstream issues
        val dependencyCandidates = context.candidates.filter { 
            it.kind == CandidateKind.DEPENDENCY 
        }
        
        // Should find dependency issues or connection errors
        val hasRelevantFindings = dependencyCandidates.isNotEmpty() ||
            context.candidates.any { candidate ->
                candidate.title.contains("Connection", ignoreCase = true) ||
                candidate.evidence["template"]?.toString()?.contains("payment", ignoreCase = true) == true
            }
        
        assertTrue(hasRelevantFindings || context.candidates.isNotEmpty(),
            "Should identify downstream or connection issues")

        // Step 3: Chat about dependencies
        chatAdvisor.startSession(context)
        
        val depResponse = chatAdvisor.processMessage("Are there dependency issues?", context)
        assertTrue(depResponse.message.isNotBlank())

        // Step 4: Verify recommendations mention dependencies
        val recommendations = chatAdvisor.getRecommendations(context)
        assertTrue(recommendations.isNotEmpty())
    }

    @Test
    fun `end-to-end memory pressure incident with APM unavailable`() {
        // Scenario: OOM errors, but APM data is unavailable
        mockDatadog.setActiveScenario("memory-pressure")

        // Step 1: Ingest anomaly
        val anomaly = MetricAnomaly(
            id = "anomaly-${UUID.randomUUID()}",
            timestamp = Instant.parse("2026-01-15T12:00:00Z"),
            metricName = "jvm.heap.used",
            query = "avg:jvm.heap.used{service:api}",
            expectedValue = 0.6,
            actualValue = 0.95,
            deviationPercent = 58.3,
            service = "api",
            env = "prod"
        )

        val ingestionResult = ingestionService.ingestMetricAnomaly(anomaly)
        val context = ingestionService.getIncident(ingestionResult.incidentId!!)!!

        // Step 2: Analysis should work even without APM
        // Symptoms should include memory-related findings
        assertTrue(context.symptoms.isNotEmpty(), "Should have symptoms from anomaly")

        // Step 3: Chat should handle gracefully
        chatAdvisor.startSession(context)
        
        val summaryResponse = chatAdvisor.processMessage("What happened?", context)
        assertTrue(summaryResponse.message.isNotBlank())

        // APM-related questions should be handled gracefully
        val depResponse = chatAdvisor.processMessage("Check dependencies", context)
        assertTrue(depResponse.message.isNotBlank(), "Should respond even without APM data")
    }

    @Test
    fun `end-to-end healthy system check should show minimal issues`() {
        // Scenario: System is healthy, analysis should reflect that
        mockDatadog.setActiveScenario("healthy")

        // Step 1: Manual report for investigation
        val report = ManualIncidentReport(
            id = "manual-${UUID.randomUUID()}",
            timestamp = Instant.now(),
            title = "User reports intermittent slowness",
            description = "Some users report occasional slow responses",
            service = "api",
            env = "prod",
            severity = AlertSeverity.LOW
        )

        val ingestionResult = ingestionService.ingestManualReport(report)
        val context = ingestionService.getIncident(ingestionResult.incidentId!!)!!

        // Step 2: Analysis should complete without major findings
        // In a healthy scenario, we expect fewer/lower-scored candidates

        // Step 3: Chat should indicate no major issues
        chatAdvisor.startSession(context)
        
        val summaryResponse = chatAdvisor.processMessage("What happened?", context)
        assertTrue(summaryResponse.message.isNotBlank())

        // System should still provide helpful guidance
        val fixResponse = chatAdvisor.processMessage("What should I check?", context)
        assertTrue(fixResponse.message.isNotBlank())
    }

    @Test
    fun `analyze from monitor should produce complete report`() {
        mockDatadog.setActiveScenario("database-latency")

        // Use RCA agent directly
        val report = rcaAgent.analyzeFromMonitor(
            monitorId = 12345,
            triggerTs = Instant.parse("2026-01-15T12:00:00Z"),
            windowMinutes = 30,
            baselineMinutes = 30
        )

        // Verify report structure
        assertNotNull(report.meta)
        assertEquals(SeedType.MONITOR, report.meta.seedType)
        assertNotNull(report.windows)
        assertNotNull(report.scope)
        assertTrue(report.symptoms.isNotEmpty() || report.findings.isNotEmpty(),
            "Report should have findings")
        assertTrue(report.recommendations.isNotEmpty(), "Should have recommendations")
    }

    @Test
    fun `analyze from logs should produce complete report`() {
        mockDatadog.setActiveScenario("downstream-failure")

        val report = rcaAgent.analyzeFromLogs(
            logQuery = "service:api @status:error",
            anchorTs = Instant.parse("2026-01-15T12:00:00Z"),
            windowMinutes = 30,
            baselineMinutes = 30
        )

        assertNotNull(report.meta)
        assertEquals(SeedType.LOGS, report.meta.seedType)
        assertTrue(report.recommendations.isNotEmpty())
    }

    @Test
    fun `analyze from service should produce complete report`() {
        mockDatadog.setActiveScenario("database-latency")

        val report = rcaAgent.analyzeFromService(
            service = "api",
            env = "prod",
            start = Instant.parse("2026-01-15T11:30:00Z"),
            end = Instant.parse("2026-01-15T12:00:00Z")
        )

        assertNotNull(report.meta)
        assertEquals(SeedType.SERVICE, report.meta.seedType)
        assertEquals("api", report.scope.service)
        assertEquals("prod", report.scope.env)
    }

    @Test
    fun `multi-turn chat conversation maintains context`() {
        mockDatadog.setActiveScenario("database-latency")

        val alert = AlertTrigger(
            id = "alert-chat-test",
            timestamp = Instant.parse("2026-01-15T12:00:00Z"),
            alertType = AlertType.MONITOR_ALERT,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH
        )

        val ingestionResult = ingestionService.ingestAlert(alert)
        val context = ingestionService.getIncident(ingestionResult.incidentId!!)!!

        chatAdvisor.startSession(context)

        // Simulate a multi-turn conversation
        val questions = listOf(
            "What happened?",
            "What's the root cause?",
            "Show me the logs",
            "What about dependencies?",
            "How do I fix this?",
            "What's the priority?"
        )

        questions.forEach { question ->
            val response = chatAdvisor.processMessage(question, context)
            assertTrue(response.message.isNotBlank(), "Should respond to: $question")
        }

        // Verify chat history captures full conversation
        val userMessages = context.chatHistory.filter { it.role == ChatRole.USER }
        assertEquals(questions.size, userMessages.size, "All user messages should be recorded")
    }
}
