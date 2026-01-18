package com.example.rca.integration

import com.example.rca.agent.ChatAdvisor
import com.example.rca.agent.ChatIntent
import com.example.rca.agent.RcaAgent
import com.example.rca.analysis.ApmAnalyzer
import com.example.rca.analysis.LogAnalyzer
import com.example.rca.analysis.MetricAnalyzer
import com.example.rca.analysis.ScoringEngine
import com.example.rca.domain.*
import com.example.rca.fixtures.TestScenarios
import com.example.rca.mock.MockDatadogClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Integration tests for chat-based incident advice.
 * Tests real chat processing with analyzed incident data.
 */
class ChatAdviceIntegrationTest {

    private lateinit var mockDatadog: MockDatadogClient
    private lateinit var rcaAgent: RcaAgent
    private lateinit var chatAdvisor: ChatAdvisor

    @BeforeEach
    fun setup() {
        mockDatadog = MockDatadogClient()
        mockDatadog.loadScenario("database-latency", TestScenarios.databaseLatencyScenario)
        mockDatadog.loadScenario("downstream-failure", TestScenarios.downstreamFailureScenario)

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
    }

    @Test
    fun `chat should provide incident summary`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("What happened?", context)

        // Assert
        assertEquals(ChatIntent.SUMMARY, response.intent)
        assertTrue(response.message.contains("Incident Summary"), "Should contain summary header")
        assertTrue(response.message.contains("Symptoms"), "Should mention symptoms")
        assertTrue(response.suggestions.isNotEmpty(), "Should provide follow-up suggestions")
    }

    @Test
    fun `chat should explain root causes when asked`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("What caused this issue?", context)

        // Assert
        assertEquals(ChatIntent.ROOT_CAUSE, response.intent)
        assertTrue(response.message.contains("Root Cause") || response.message.contains("Candidates"),
            "Should discuss root causes")
    }

    @Test
    fun `chat should describe log patterns`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("Show me the error logs", context)

        // Assert
        assertEquals(ChatIntent.LOGS, response.intent)
        // Either shows patterns or explains why there aren't any
        assertTrue(response.message.contains("Log") || response.message.contains("pattern"),
            "Should discuss log patterns")
    }

    @Test
    fun `chat should suggest remediation steps`() {
        // Arrange
        mockDatadog.setActiveScenario("downstream-failure")
        val context = createAnalyzedContext("downstream-failure")

        // Act
        val response = chatAdvisor.processMessage("How do I fix this?", context)

        // Assert
        assertEquals(ChatIntent.REMEDIATION, response.intent)
        assertTrue(response.message.contains("Recommend") || response.message.contains("Action"),
            "Should provide recommendations")
    }

    @Test
    fun `chat should explain timeline`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("When did this start?", context)

        // Assert
        assertEquals(ChatIntent.TIMELINE, response.intent)
        assertTrue(response.message.contains("Timeline") || response.message.contains("period"),
            "Should discuss timeline")
    }

    @Test
    fun `chat should assess severity`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("How bad is this?", context)

        // Assert
        assertEquals(ChatIntent.SEVERITY, response.intent)
        assertTrue(response.message.contains("Severity") || response.message.contains("severity"),
            "Should assess severity")
    }

    @Test
    fun `chat should explain dependencies`() {
        // Arrange
        mockDatadog.setActiveScenario("downstream-failure")
        val context = createAnalyzedContext("downstream-failure")

        // Act
        val response = chatAdvisor.processMessage("Are there dependency issues?", context)

        // Assert
        assertEquals(ChatIntent.DEPENDENCY, response.intent)
        assertTrue(response.message.contains("Dependency") || response.message.contains("dependency") 
            || response.message.contains("dependencies"),
            "Should discuss dependencies")
    }

    @Test
    fun `chat should handle general questions gracefully`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val response = chatAdvisor.processMessage("Hello, can you help?", context)

        // Assert
        assertEquals(ChatIntent.GENERAL, response.intent)
        assertTrue(response.message.contains("help") || response.message.contains("ask"),
            "Should offer to help")
        assertTrue(response.suggestions.isNotEmpty(), "Should suggest valid questions")
    }

    @Test
    fun `chat history should be maintained across messages`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act - Send multiple messages
        chatAdvisor.processMessage("What happened?", context)
        chatAdvisor.processMessage("What's the root cause?", context)
        chatAdvisor.processMessage("How do I fix it?", context)

        // Assert
        val history = context.chatHistory
        assertTrue(history.size >= 6, "Should have user + assistant messages")
        
        val userMessages = history.filter { it.role == ChatRole.USER }
        val assistantMessages = history.filter { it.role == ChatRole.ASSISTANT }
        
        assertEquals(3, userMessages.size, "Should have 3 user messages")
        assertEquals(3, assistantMessages.size, "Should have 3 assistant responses")
    }

    @Test
    fun `get recommendations should return actionable items`() {
        // Arrange
        mockDatadog.setActiveScenario("downstream-failure")
        val context = createAnalyzedContext("downstream-failure")

        // Act
        val recommendations = chatAdvisor.getRecommendations(context)

        // Assert
        assertTrue(recommendations.isNotEmpty(), "Should provide recommendations")
        assertTrue(recommendations.size <= 5, "Should limit to 5 recommendations")
        recommendations.forEach { rec ->
            assertTrue(rec.isNotBlank(), "Each recommendation should be non-empty")
        }
    }

    @Test
    fun `chat session should be created for new incidents`() {
        // Arrange
        mockDatadog.setActiveScenario("database-latency")
        val context = createAnalyzedContext("database-latency")

        // Act
        val session = chatAdvisor.startSession(context)

        // Assert
        assertNotNull(session)
        assertTrue(session.id.startsWith("chat-"), "Session ID should have prefix")
        assertEquals(context.id, session.incidentId)
        
        // Should have system context in chat history
        val systemMessages = context.chatHistory.filter { it.role == ChatRole.SYSTEM }
        assertTrue(systemMessages.isNotEmpty(), "Should have system context message")
    }

    // Helper to create an analyzed incident context
    private fun createAnalyzedContext(scenarioName: String): IncidentContext {
        mockDatadog.setActiveScenario(scenarioName)
        
        val baseTime = Instant.parse("2026-01-15T12:00:00Z")
        val windows = Windows.endingAt(baseTime, windowMinutes = 30, baselineMinutes = 30)
        val scope = Scope(service = "api", env = "prod")

        val context = IncidentContext(
            id = "TEST-$scenarioName",
            windows = windows,
            scope = scope
        )

        // Run analysis to populate context
        rcaAgent.startAnalysis(context)

        return context
    }
}
