package com.example.rca.integration

import com.example.rca.dice.DiceIngestionService
import com.example.rca.dice.DiceProposition
import com.example.rca.dice.model.AlertSeverity
import com.example.rca.dice.model.AlertTrigger
import com.example.rca.dice.model.AlertType
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

/**
 * End-to-end integration test using REAL HTTP calls to dice-server.
 *
 * This test:
 * 1. Uses real HTTP clients (not mocked) to communicate with dice-server
 * 2. Assumes dice-server is running on localhost:8080 (started by run-integration-tests.sh)
 * 3. Tests the full flow: RCA Agent -> Dice Ingestion -> dice-server -> LLM -> Response
 *
 * Unlike SystemIntegrationTest, this uses a separate HTTP client configuration to avoid Spring Boot
 * test framework managing the dice-server connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Disabled("EndToEndIntegrationTest is currently disabled")
@EnabledIfEnvironmentVariable(
        named = "DICE_SERVER_URL",
        matches = ".*",
        disabledReason = "Set DICE_SERVER_URL env var to run E2E test"
)
class EndToEndIntegrationTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired private lateinit var mockDatadogClient: MockDatadogClient

    @Autowired private lateinit var diceIngestionService: DiceIngestionService

    // Real HTTP client for dice-server (separate from Spring-managed beans)
    private lateinit var realDiceClient: RealDiceClient

    @BeforeEach
    fun setUp() {
        val setupStartTime = System.currentTimeMillis()
        val diceServerUrl = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"
        logger.info("")
        logger.info("========================================")
        logger.info("Setting up EndToEndIntegrationTest...")
        logger.info("========================================")
        logger.info("Connecting to dice-server at: $diceServerUrl")
        logger.info("Setup started at: ${Instant.now()}")

        // Create a real HTTP client with timeouts
        logger.info("Creating RestTemplate with timeouts...")
        logger.info("  - Connect timeout: 10 seconds")
        logger.info("  - Read timeout: 120 seconds")
        val restTemplateStart = System.currentTimeMillis()
        val restTemplate =
                RestTemplateBuilder()
                        .setConnectTimeout(Duration.ofSeconds(10))
                        .setReadTimeout(Duration.ofSeconds(120)) // 2 min for LLM calls
                        .build()
        val restTemplateDuration = System.currentTimeMillis() - restTemplateStart
        logger.info("✓ RestTemplate created in ${restTemplateDuration}ms")

        realDiceClient = RealDiceClient(diceServerUrl, restTemplate)
        logger.info("✓ RealDiceClient initialized")

        // Verify dice-server is accessible with timeout protection
        logger.info("")
        logger.info("Verifying dice-server health...")
        logger.info("  Making HTTP GET request to: $diceServerUrl/actuator/health")
        logger.info("  Using timeout wrapper (max 15 seconds)...")
        val healthCheckStart = System.currentTimeMillis()

        val health =
                try {
                    // Use a timeout wrapper to prevent indefinite hanging
                    val future =
                            CompletableFuture.supplyAsync {
                                logger.info(
                                        "  [HealthCheck] Starting HTTP request in background thread..."
                                )
                                realDiceClient.checkHealth()
                            }

                    // Wait with timeout
                    future.get(15, TimeUnit.SECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    val healthCheckDuration = System.currentTimeMillis() - healthCheckStart
                    logger.error(
                            "✗ Health check timed out after ${healthCheckDuration}ms (15 second limit)"
                    )
                    logger.error("  The dice-server may not be responding or is too slow")
                    logger.error(
                            "  Check if server is running: curl $diceServerUrl/actuator/health"
                    )
                    throw RuntimeException("Health check timed out after 15 seconds", e)
                } catch (e: Exception) {
                    val healthCheckDuration = System.currentTimeMillis() - healthCheckStart
                    logger.error(
                            "✗ Health check failed after ${healthCheckDuration}ms: ${e.message}"
                    )
                    logger.error("  Exception type: ${e.javaClass.simpleName}")
                    if (e.cause != null) {
                        logger.error(
                                "  Caused by: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}"
                        )
                    }
                    throw e
                }

        val healthCheckDuration = System.currentTimeMillis() - healthCheckStart
        logger.info("✓ Health check completed in ${healthCheckDuration}ms")
        assertNotNull(health, "dice-server should be reachable at $diceServerUrl")
        logger.info("✓ dice-server health check passed: $health")

        val setupDuration = System.currentTimeMillis() - setupStartTime
        logger.info("")
        logger.info("✓ Test setup complete in ${setupDuration}ms")
        logger.info("========================================")
        logger.info("")
    }

    @Test
    fun `test full E2E flow from alert ingestion to dice query`() {
        val testStartTime = System.currentTimeMillis()
        logger.info("")
        logger.info("========================================")
        logger.info("=== Starting E2E Integration Test ===")
        logger.info("========================================")
        logger.info("Test started at: ${Instant.now()}")

        // 1. Setup mock Datadog scenario
        logger.info("")
        logger.info("[STEP 1/5] Setting up mock Datadog scenario...")
        val incidentStart = Instant.now().minusSeconds(600)
        val testScenario =
                scenario("e2e-checkout-failure") {
                    description("Checkout service failing due to DB timeout")
                    incidentStart(incidentStart)
                }
        mockDatadogClient.loadScenario("e2e-checkout-failure", testScenario)
        mockDatadogClient.setActiveScenario("e2e-checkout-failure")
        logger.info("✓ Mock Datadog scenario loaded: e2e-checkout-failure")

        // 2. Create alert trigger
        logger.info("")
        logger.info("[STEP 2/5] Creating alert trigger...")
        val trigger =
                AlertTrigger(
                        id = "e2e-alert-${System.currentTimeMillis()}",
                        timestamp = Instant.now(),
                        alertType = AlertType.MONITOR_ALERT,
                        monitorId = 99999L,
                        service = "checkout-service",
                        env = "prod",
                        severity = AlertSeverity.CRITICAL,
                        message = "P95 Latency > 2s in checkout-service"
                )
        logger.info("✓ Created alert trigger:")
        logger.info("  - ID: ${trigger.id}")
        logger.info("  - Service: ${trigger.service}")
        logger.info("  - Severity: ${trigger.severity}")
        logger.info("  - Message: ${trigger.message}")

        // 3. Ingest alert via RCA agent (this calls dice-server via real HTTP)
        logger.info("")
        logger.info("[STEP 3/5] Ingesting alert via DiceIngestionService...")
        logger.info(
                "  This will make HTTP call to dice-server at: ${System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"}"
        )
        val ingestStartTime = System.currentTimeMillis()
        val result = diceIngestionService.ingestAlert(trigger)
        val ingestDuration = System.currentTimeMillis() - ingestStartTime
        logger.info("✓ Ingest completed in ${ingestDuration}ms")
        logger.info("  - Success: ${result.success}")
        logger.info("  - Incident ID: ${result.incidentId}")
        logger.info("  - Message: ${result.message}")

        assertTrue(result.success, "Alert ingestion should succeed")
        val incidentId = result.incidentId!!
        assertNotNull(incidentId, "Should have incident ID")
        logger.info("✓ Incident created: $incidentId")

        // 4. Wait for dice-server to process (LLM extraction takes time)
        logger.info("")
        logger.info("[STEP 4/5] Waiting for dice-server to process propositions...")
        logger.info("  This may take 10-60 seconds (LLM processing time)")
        logger.info("  Polling dice-server every 2 seconds...")
        var propositions = emptyList<DiceProposition>()
        var attempts = 0
        val maxAttempts = 30 // 30 attempts * 2 seconds = 60 seconds max wait
        val pollStartTime = System.currentTimeMillis()

        while (propositions.isEmpty() && attempts < maxAttempts) {
            Thread.sleep(2000)
            attempts++
            val elapsed = (System.currentTimeMillis() - pollStartTime) / 1000
            logger.info(
                    "  [Attempt $attempts/$maxAttempts] (${elapsed}s elapsed) Checking for propositions..."
            )
            propositions = realDiceClient.listPropositions(incidentId)
            if (propositions.isNotEmpty()) {
                logger.info("  ✓ Found ${propositions.size} proposition(s)!")
                propositions.forEachIndexed { index, prop ->
                    logger.info("    Proposition ${index + 1}:")
                    logger.info("      - ID: ${prop.id}")
                    logger.info(
                            "      - Text: ${prop.text.take(100)}${if (prop.text.length > 100) "..." else ""}"
                    )
                    logger.info("      - Confidence: ${prop.confidence}")
                }
            } else {
                logger.info("  ⏳ No propositions yet, waiting...")
            }
        }

        val pollDuration = (System.currentTimeMillis() - pollStartTime) / 1000
        assertTrue(
                propositions.isNotEmpty(),
                "dice-server should have extracted at least one proposition after $attempts attempts (${pollDuration}s)"
        )
        logger.info("✓ Proposition extraction completed in ${pollDuration}s")

        // 5. Query dice-server for reasoning (real LLM call)
        logger.info("")
        logger.info("[STEP 5/5] Querying dice-server for reasoning...")
        logger.info("  Question: 'What service is affected and what is the probable cause?'")
        logger.info("  This will make an LLM call (may take 10-30 seconds)...")
        val queryStartTime = System.currentTimeMillis()
        val answer =
                realDiceClient.query(
                        incidentId,
                        "What service is affected and what is the probable cause?"
                )
        val queryDuration = System.currentTimeMillis() - queryStartTime
        logger.info("✓ Query completed in ${queryDuration}ms")
        logger.info("")
        logger.info("  Answer received (${answer.length} chars):")
        logger.info("  ┌─────────────────────────────────────────────────────────")
        answer.lines().forEach { line -> logger.info("  │ $line") }
        logger.info("  └─────────────────────────────────────────────────────────")

        assertNotNull(answer)
        assertTrue(answer.isNotBlank(), "Should have a non-empty answer")
        assertTrue(
                answer.contains("checkout-service", ignoreCase = true),
                "Answer should mention checkout-service. Got: $answer"
        )
        logger.info("✓ Answer validation passed")

        val totalDuration = (System.currentTimeMillis() - testStartTime) / 1000.0
        logger.info("")
        logger.info("========================================")
        logger.info("=== E2E Integration Test PASSED ===")
        logger.info("========================================")
        logger.info("Total test duration: ${totalDuration}s")
        logger.info("  - Ingest: ${ingestDuration}ms")
        logger.info("  - Polling: ${pollDuration}s")
        logger.info("  - Query: ${queryDuration}ms")
        logger.info("Test completed at: ${Instant.now()}")
        logger.info("")
    }

    /**
     * Real HTTP client for dice-server. Uses RestTemplate directly (not Spring-managed) to avoid
     * test framework interference.
     */
    private class RealDiceClient(
            private val baseUrl: String,
            private val restTemplate: RestTemplate
    ) {
        private val logger = LoggerFactory.getLogger(RealDiceClient::class.java)

        fun checkHealth(): String? {
            val healthUrl = "$baseUrl/actuator/health"
            logger.info("  [RealDiceClient] Attempting health check...")
            logger.info("  [RealDiceClient] URL: $healthUrl")
            val startTime = System.currentTimeMillis()
            return try {
                logger.info(
                        "  [RealDiceClient] Making GET request (connect timeout: 10s, read timeout: 120s)..."
                )
                val response = restTemplate.getForObject(healthUrl, Map::class.java)
                val duration = System.currentTimeMillis() - startTime
                val status = response?.get("status")?.toString()
                logger.info("  [RealDiceClient] ✓ Health check succeeded in ${duration}ms")
                logger.info("  [RealDiceClient] Response status: $status")
                logger.info("  [RealDiceClient] Full response: $response")
                status
            } catch (e: java.net.SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime
                logger.error("  [RealDiceClient] ✗ Health check timed out after ${duration}ms")
                logger.error("  [RealDiceClient] Timeout type: ${e.javaClass.simpleName}")
                logger.error("  [RealDiceClient] Message: ${e.message}")
                throw e
            } catch (e: java.net.ConnectException) {
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                        "  [RealDiceClient] ✗ Health check connection failed after ${duration}ms"
                )
                logger.error("  [RealDiceClient] Connection refused - is dice-server running?")
                logger.error("  [RealDiceClient] Message: ${e.message}")
                throw e
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                logger.error(
                        "  [RealDiceClient] ✗ Health check failed after ${duration}ms: ${e.javaClass.simpleName}"
                )
                logger.error("  [RealDiceClient] Message: ${e.message}")
                logger.error("  [RealDiceClient] Stack trace:", e)
                throw e
            }
        }

        fun listPropositions(contextId: String): List<DiceProposition> {
            return try {
                val url = "$baseUrl/api/v1/contexts/$contextId/memory"
                logger.debug("Fetching propositions from: $url")
                val response = restTemplate.getForObject(url, Map::class.java)
                val props = response?.get("propositions") as? List<Map<String, Any>>
                val propositions = props?.map { mapToProposition(it) } ?: emptyList()
                logger.debug("Retrieved ${propositions.size} propositions")
                propositions
            } catch (e: Exception) {
                logger.warn("Failed to list propositions: ${e.message}", e)
                emptyList()
            }
        }

        fun query(contextId: String, question: String): String {
            return try {
                val url = "$baseUrl/api/v1/contexts/$contextId/query"
                val request = mapOf("question" to question)
                logger.debug("Sending query to: $url")
                logger.debug("Question: $question")
                val queryStart = System.currentTimeMillis()
                val response = restTemplate.postForObject(url, request, Map::class.java)
                val queryDuration = System.currentTimeMillis() - queryStart
                val answer = (response?.get("answer") as? String) ?: "No answer from dice-server"
                logger.debug(
                        "Query completed in ${queryDuration}ms, answer length: ${answer.length}"
                )
                answer
            } catch (e: Exception) {
                logger.error("Query failed: ${e.message}", e)
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
