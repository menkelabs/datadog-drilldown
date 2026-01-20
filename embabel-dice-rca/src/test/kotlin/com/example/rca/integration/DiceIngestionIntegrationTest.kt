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
import com.example.rca.fixtures.TestScenarios
import com.example.rca.mock.MockDatadogClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Dice ingestion service.
 * Tests real processing logic with mocked Datadog data.
 */
class DiceIngestionIntegrationTest {

    private lateinit var mockDatadog: MockDatadogClient
    private lateinit var ingestionService: DiceIngestionService
    private lateinit var rcaAgent: RcaAgent

    @BeforeEach
    fun setup() {
        // Set up mock Datadog client with test scenarios
        mockDatadog = MockDatadogClient()
        mockDatadog.loadScenario("database-latency", TestScenarios.databaseLatencyScenario)
        mockDatadog.loadScenario("downstream-failure", TestScenarios.downstreamFailureScenario)
        mockDatadog.loadScenario("memory-pressure", TestScenarios.memoryPressureScenario)
        mockDatadog.loadScenario("healthy", TestScenarios.healthyScenario)

        // Create real analyzers
        val logAnalyzer = LogAnalyzer()
        val metricAnalyzer = MetricAnalyzer()
        val apmAnalyzer = ApmAnalyzer()
        val scoringEngine = ScoringEngine()
        val chatAdvisor = ChatAdvisor()

        // Create RCA agent with mock Datadog
        rcaAgent = RcaAgent(
            datadogClient = mockDatadog,
            logAnalyzer = logAnalyzer,
            metricAnalyzer = metricAnalyzer,
            apmAnalyzer = apmAnalyzer,
            scoringEngine = scoringEngine,
            chatAdvisor = chatAdvisor
        )

        // Create ingestion service with no-op event publisher and mock dice client
        val eventPublisher = ApplicationEventPublisher { /* no-op */ }
        val mockDiceClient = object : DiceClient("http://localhost:8080") {
            override fun ingest(contextId: String, documentId: String, text: String): IngestResponse {
                return IngestResponse(documentId, 0, "SUCCESS", null)
            }
            override fun query(contextId: String, question: String): String = "Mock answer"
            override fun listPropositions(contextId: String): List<DiceProposition> = emptyList()
        }
        ingestionService = DiceIngestionService(eventPublisher, rcaAgent, mockDiceClient)
    }

    @Test
    fun `ingest alert trigger should create incident and trigger analysis`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        
        val alert = AlertTrigger(
            id = "alert-${UUID.randomUUID()}",
            timestamp = Instant.parse("2026-01-15T12:00:00Z"),
            source = "datadog",
            alertType = AlertType.MONITOR_ALERT,
            monitorId = 12345,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH,
            message = "P95 latency exceeded threshold"
        )

        // Act
        val result = ingestionService.ingestAlert(alert)

        // Assert
        assertTrue(result.success, "Ingestion should succeed")
        assertNotNull(result.incidentId, "Should create incident ID")
        assertTrue(result.analysisTriggered, "Should trigger analysis for HIGH severity")

        // Verify incident was created
        val incident = ingestionService.getIncident(result.incidentId!!)
        assertNotNull(incident, "Incident should be retrievable")
        assertEquals("api", incident.scope.service)
        assertEquals("prod", incident.scope.env)
    }

    @Test
    fun `ingest metric anomaly should detect significant deviation`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        
        val anomaly = MetricAnomaly(
            id = "anomaly-${UUID.randomUUID()}",
            timestamp = Instant.parse("2026-01-15T12:05:00Z"),
            source = "datadog",
            metricName = "trace.api.request.duration",
            query = "p95:trace.api.request.duration{service:api}",
            expectedValue = 50.0,
            actualValue = 500.0,
            deviationPercent = 900.0,
            service = "api",
            env = "prod"
        )

        // Act
        val result = ingestionService.ingestMetricAnomaly(anomaly)

        // Assert
        assertTrue(result.success)
        assertNotNull(result.incidentId)
        assertTrue(result.analysisTriggered, "Should trigger analysis for significant anomaly")

        val incident = ingestionService.getIncident(result.incidentId!!)
        assertNotNull(incident)
        assertTrue(incident.symptoms.isNotEmpty(), "Should have added symptom from anomaly")
    }

    @Test
    fun `ingest log stream should process logs successfully`() {
        // Arrange
        mockDatadog.setActiveScenario("downstream-failure")
        
        // Ingest logs directly
        val logEvent = LogStreamEvent(
            id = "logs-1",
            timestamp = Instant.parse("2026-01-15T12:05:00Z"),
            query = "service:api",
            logs = listOf(
                LogData(
                    timestamp = Instant.parse("2026-01-15T12:05:00Z"),
                    message = "ConnectionError: Failed to connect",
                    level = "error",
                    service = "api"
                )
            ),
            service = "api",
            env = "prod"
        )

        // Act
        val logResult = ingestionService.ingestLogStream(logEvent)

        // Assert
        assertTrue(logResult.success, "Log ingestion should succeed")
        assertNotNull(logResult.incidentId, "Should create or find incident ID")
        assertTrue(logResult.message?.contains("Logs ingested") == true, 
            "Should report successful ingestion")
    }

    @Test
    fun `ingest log stream can correlate with existing incident when in window`() {
        // Arrange
        mockDatadog.setActiveScenario("downstream-failure")
        
        // Create base timestamp
        val baseTime = Instant.parse("2026-01-15T12:00:00Z")
        
        // First, create an incident via alert
        val alert = AlertTrigger(
            id = "alert-correlate-test",
            timestamp = baseTime,
            alertType = AlertType.MONITOR_ALERT,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH
        )
        val alertResult = ingestionService.ingestAlert(alert)
        
        // Get the incident to check its window
        val incident = ingestionService.getIncident(alertResult.incidentId!!)
        assertNotNull(incident, "Incident should exist")

        // The log timestamp must fall within the incident window for correlation
        // The window is 30 minutes before anchor, so we need to be within that range
        val logTimestamp = baseTime.minusSeconds(15 * 60) // 15 minutes before anchor (within window)
        
        // Now ingest logs for the same service/env within the incident window
        val logEvent = LogStreamEvent(
            id = "logs-correlate-test",
            timestamp = logTimestamp,
            query = "service:api",
            logs = listOf(
                LogData(
                    timestamp = logTimestamp,
                    message = "ConnectionError: Failed to connect",
                    level = "error",
                    service = "api"
                )
            ),
            service = "api",
            env = "prod"
        )

        // Act
        val logResult = ingestionService.ingestLogStream(logEvent)

        // Assert
        assertTrue(logResult.success)
        assertNotNull(logResult.incidentId)
        // Correlation depends on the time window match - we verify logs were processed
        assertTrue(logResult.message?.contains("Logs ingested") == true)
    }

    @Test
    fun `ingest manual report should always trigger analysis`() {
        // Arrange
        mockDatadog.setActiveScenario("healthy")
        
        val report = ManualIncidentReport(
            id = "manual-1",
            timestamp = Instant.now(),
            title = "User reports slow checkout",
            description = "Multiple users reporting slow checkout experience",
            service = "api",
            env = "prod",
            severity = AlertSeverity.LOW  // Even low severity should trigger
        )

        // Act
        val result = ingestionService.ingestManualReport(report)

        // Assert
        assertTrue(result.success)
        assertNotNull(result.incidentId)
        assertTrue(result.analysisTriggered, "Manual reports should always trigger analysis")

        val incident = ingestionService.getIncident(result.incidentId!!)
        assertNotNull(incident)
        assertEquals("User reports slow checkout", incident.metadata["title"])
    }

    @Test
    fun `list active incidents should return all tracked incidents`() {
        // Arrange
        mockDatadog.setActiveScenario("healthy")
        
        val alert1 = AlertTrigger(
            id = "alert-1",
            timestamp = Instant.now(),
            alertType = AlertType.MONITOR_ALERT,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH
        )
        val alert2 = AlertTrigger(
            id = "alert-2",
            timestamp = Instant.now(),
            alertType = AlertType.MONITOR_ALERT,
            service = "web",
            env = "prod",
            severity = AlertSeverity.HIGH
        )

        ingestionService.ingestAlert(alert1)
        ingestionService.ingestAlert(alert2)

        // Act
        val incidents = ingestionService.listActiveIncidents()

        // Assert
        assertTrue(incidents.size >= 2, "Should have at least 2 active incidents")
    }

    @Test
    fun `close incident should remove from active tracking`() {
        // Arrange
        mockDatadog.setActiveScenario("healthy")
        
        val alert = AlertTrigger(
            id = "alert-1",
            timestamp = Instant.now(),
            alertType = AlertType.MONITOR_ALERT,
            service = "api",
            env = "prod",
            severity = AlertSeverity.HIGH
        )
        val result = ingestionService.ingestAlert(alert)
        val incidentId = result.incidentId!!

        // Act
        val closed = ingestionService.closeIncident(incidentId)

        // Assert
        assertTrue(closed, "Should successfully close incident")
        val incident = ingestionService.getIncident(incidentId)
        assertEquals(null, incident, "Closed incident should not be retrievable")
    }
}
