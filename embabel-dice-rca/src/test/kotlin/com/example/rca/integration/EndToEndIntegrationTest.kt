package com.example.rca.integration

import com.example.rca.dice.DiceClient
import com.example.rca.dice.DiceIngestionService
import com.example.rca.dice.DiceProposition
import com.example.rca.dice.IngestResponse
import com.example.rca.dice.model.AlertSeverity
import com.example.rca.dice.model.AlertTrigger
import com.example.rca.dice.model.AlertType
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration test using REAL HTTP calls to dice-server.
 * 
 * This test:
 * 1. Uses real HTTP clients (not mocked) to communicate with dice-server
 * 2. Assumes dice-server is running on localhost:8080 (started by run-integration-tests.sh)
 * 3. Tests the full flow: RCA Agent -> Dice Ingestion -> dice-server -> LLM -> Response
 * 
 * Unlike SystemIntegrationTest, this uses a separate HTTP client configuration
 * to avoid Spring Boot test framework managing the dice-server connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DICE_SERVER_URL", matches = ".*", disabledReason = "Set DICE_SERVER_URL env var to run E2E test")
class EndToEndIntegrationTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var mockDatadogClient: MockDatadogClient

    @Autowired
    private lateinit var diceIngestionService: DiceIngestionService

    // Real HTTP client for dice-server (separate from Spring-managed beans)
    private lateinit var realDiceClient: RealDiceClient

    @BeforeEach
    fun setUp() {
        val diceServerUrl = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"
        logger.info("Connecting to dice-server at: $diceServerUrl")
        
        // Create a real HTTP client with timeouts
        val restTemplate = RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(120)) // 2 min for LLM calls
            .build()
        
        realDiceClient = RealDiceClient(diceServerUrl, restTemplate)
        
        // Verify dice-server is accessible
        val health = realDiceClient.checkHealth()
        assertNotNull(health, "dice-server should be reachable at $diceServerUrl")
        logger.info("dice-server health check passed: $health")
    }

    @Test
    fun `test full E2E flow from alert ingestion to dice query`() {
        logger.info("=== Starting E2E Integration Test ===")
        
        // 1. Setup mock Datadog scenario
        val incidentStart = Instant.now().minusSeconds(600)
        val testScenario = scenario("e2e-checkout-failure") {
            description("Checkout service failing due to DB timeout")
            incidentStart(incidentStart)
        }
        mockDatadogClient.loadScenario("e2e-checkout-failure", testScenario)
        mockDatadogClient.setActiveScenario("e2e-checkout-failure")
        logger.info("Mock Datadog scenario loaded")

        // 2. Create alert trigger
        val trigger = AlertTrigger(
            id = "e2e-alert-${System.currentTimeMillis()}",
            timestamp = Instant.now(),
            alertType = AlertType.MONITOR_ALERT,
            monitorId = 99999L,
            service = "checkout-service",
            env = "prod",
            severity = AlertSeverity.CRITICAL,
            message = "P95 Latency > 2s in checkout-service"
        )
        logger.info("Created alert trigger: ${trigger.id}")

        // 3. Ingest alert via RCA agent (this calls dice-server via real HTTP)
        logger.info("Ingesting alert via DiceIngestionService...")
        val result = diceIngestionService.ingestAlert(trigger)
        logger.info("Ingest result: success=${result.success}, incidentId=${result.incidentId}")
        
        assertTrue(result.success, "Alert ingestion should succeed")
        val incidentId = result.incidentId!!
        assertNotNull(incidentId, "Should have incident ID")

        // 4. Wait for dice-server to process (LLM extraction takes time)
        logger.info("Waiting for dice-server to process propositions (this may take 10-30 seconds)...")
        var propositions = emptyList<DiceProposition>()
        var attempts = 0
        val maxAttempts = 30 // 30 attempts * 2 seconds = 60 seconds max wait
        
        while (propositions.isEmpty() && attempts < maxAttempts) {
            Thread.sleep(2000)
            attempts++
            propositions = realDiceClient.listPropositions(incidentId)
            logger.info("Attempt $attempts: Found ${propositions.size} propositions")
        }

        assertTrue(propositions.isNotEmpty(), "dice-server should have extracted at least one proposition after $attempts attempts")

        // 5. Query dice-server for reasoning (real LLM call)
        logger.info("Querying dice-server for reasoning...")
        val answer = realDiceClient.query(incidentId, "What service is affected and what is the probable cause?")
        logger.info("Query answer: $answer")
        
        assertNotNull(answer)
        assertTrue(answer.isNotBlank(), "Should have a non-empty answer")
        assertTrue(answer.contains("checkout-service", ignoreCase = true), 
            "Answer should mention checkout-service. Got: $answer")

        logger.info("=== E2E Integration Test PASSED ===")
    }

    /**
     * Real HTTP client for dice-server.
     * Uses RestTemplate directly (not Spring-managed) to avoid test framework interference.
     */
    private class RealDiceClient(
        private val baseUrl: String,
        private val restTemplate: RestTemplate
    ) {
        private val logger = LoggerFactory.getLogger(RealDiceClient::class.java)

        fun checkHealth(): String? {
            return try {
                val response = restTemplate.getForObject("$baseUrl/actuator/health", Map::class.java)
                response?.get("status")?.toString()
            } catch (e: Exception) {
                logger.error("Health check failed: ${e.message}")
                null
            }
        }

        fun listPropositions(contextId: String): List<DiceProposition> {
            return try {
                val url = "$baseUrl/api/v1/contexts/$contextId/memory"
                val response = restTemplate.getForObject(url, Map::class.java)
                val props = response?.get("propositions") as? List<Map<String, Any>>
                props?.map { mapToProposition(it) } ?: emptyList()
            } catch (e: Exception) {
                logger.warn("Failed to list propositions: ${e.message}")
                emptyList()
            }
        }

        fun query(contextId: String, question: String): String {
            return try {
                val url = "$baseUrl/api/v1/contexts/$contextId/query"
                val request = mapOf("question" to question)
                val response = restTemplate.postForObject(url, request, Map::class.java)
                (response?.get("answer") as? String) ?: "No answer from dice-server"
            } catch (e: Exception) {
                logger.error("Query failed: ${e.message}")
                throw e
            }
        }

        private fun mapToProposition(map: Map<String, Any>): DiceProposition {
            return DiceProposition(
                id = map["id"] as? String ?: "",
                text = map["text"] as? String ?: "",
                confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0,
                reasoning = map["reasoning"] as? String
            )
        }
    }
}
