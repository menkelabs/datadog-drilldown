package com.example.rca.integration

import com.example.rca.agent.RcaAgent
import com.example.rca.dice.DiceClient
import com.example.rca.dice.DiceIngestionService
import com.example.rca.dice.globalTestReportCollector
import com.example.rca.dice.model.AlertSeverity
import com.example.rca.dice.model.AlertTrigger
import com.example.rca.dice.model.AlertType
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * End-to-end integration test harness.
 *
 * FLOW:
 * 1. Setup Mock Datadog Client with fake incident data (Interface implementation)
 * 2. Simulate Datadog Alert Event (Trigger)
 * 3. Run RCA Analysis Agent using Mock Datadog data
 * 4. PUSH Analysis results to REAL dice-server module (via HTTP)
 * 5. QUERY REAL dice-server to verify it used Embabel logic to extract/reason
 *
 * NOTE: This test requires:
 * - A running dice-server instance on localhost:8080
 * - OpenAI API key (OPENAI_API_KEY env var)
 *
 * To run: Start dice-server, set OPENAI_API_KEY and DICE_SERVER_URL
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(
        named = "DICE_SERVER_URL",
        matches = ".*",
        disabledReason = "Set DICE_SERVER_URL env var to run integration test"
)
class SystemIntegrationTest {

        private val logger = LoggerFactory.getLogger(javaClass)

        @Autowired lateinit var diceIngestionService: DiceIngestionService

        @Autowired lateinit var diceClient: DiceClient

        @Autowired lateinit var rcaAgent: RcaAgent

        @Autowired lateinit var datadogClient: MockDatadogClient

        @BeforeEach
        fun setup() {
                logger.info("")
                logger.info("========================================")
                logger.info(">>> @BeforeEach: SystemIntegrationTest setup")
                logger.info("========================================")
                logger.info(">>> Checking if beans are initialized...")
                try {
                        logger.info(
                                "  - diceIngestionService initialized: ${::diceIngestionService.isInitialized}"
                        )
                        logger.info("  - diceClient initialized: ${::diceClient.isInitialized}")
                        logger.info("  - rcaAgent initialized: ${::rcaAgent.isInitialized}")
                        logger.info(
                                "  - datadogClient initialized: ${::datadogClient.isInitialized}"
                        )
                        logger.info(">>> @BeforeEach: Setup complete")
                } catch (e: Exception) {
                        logger.error(">>> @BeforeEach: Error checking beans: ${e.message}", e)
                        throw e
                }
        }

        @Test
        @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.MINUTES)
        fun `test full integration from alert to dice query`() {
                val testStartTime = System.currentTimeMillis()
                logger.info("")
                logger.info("========================================")
                logger.info(">>> TEST START: SystemIntegrationTest")
                logger.info("========================================")
                logger.info("Test started at: ${Instant.now()}")
                logger.info(
                        "DICE_SERVER_URL: ${System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"}"
                )

                // Verify beans are injected
                logger.info(">>> Verifying Spring beans are injected...")
                logger.info(
                        "  - diceIngestionService: ${if (::diceIngestionService.isInitialized) "✓" else "✗"}"
                )
                logger.info("  - diceClient: ${if (::diceClient.isInitialized) "✓" else "✗"}")
                logger.info("  - rcaAgent: ${if (::rcaAgent.isInitialized) "✓" else "✗"}")
                logger.info("  - datadogClient: ${if (::datadogClient.isInitialized) "✓" else "✗"}")
                logger.info(">>> All beans verified")

                // 1. Setup mock Datadog scenario (Fake Datadog data via Interface)
                logger.info(">>> Step 1: Setting up mock Datadog scenario...")
                val incidentStart = Instant.now().minusSeconds(600)
                val testScenario =
                        scenario("checkout-failure-scenario") {
                                description("Checkout service failing due to DB timeout")
                                incidentStart(incidentStart)
                                // Add fake data points that the agent will analyze
                        }
                logger.info(">>> Scenario created, loading into datadogClient...")
                datadogClient.loadScenario("checkout-failure-scenario", testScenario)
                datadogClient.setActiveScenario("checkout-failure-scenario")
                logger.info(">>> Scenario loaded successfully")
                logger.info(">>> Creating alert trigger...")

                // 2. Trigger an alert (Fake Event from Datadog)
                val trigger =
                        AlertTrigger(
                                id = "dd-alert-999",
                                timestamp = Instant.now(),
                                alertType = AlertType.MONITOR_ALERT,
                                monitorId = 54321,
                                service = "checkout-service",
                                env = "prod",
                                severity = AlertSeverity.CRITICAL,
                                message = "P95 Latency > 2s in checkout-service"
                        )

                // 3. Process Alert -> Analysis (Mock DD) -> Dice Ingestion (Real DICE REST)
                // This validates that our ingestion logic correctly calls the dice-server
                logger.info(">>> Step 3: Processing alert via diceIngestionService...")
                logger.info(">>> About to call diceIngestionService.ingestAlert()")
                logger.info(">>> This will make HTTP calls to dice-server")
                val ingestStartTime = System.currentTimeMillis()

                val result =
                        try {
                                diceIngestionService.ingestAlert(trigger)
                        } catch (e: Exception) {
                                val duration = System.currentTimeMillis() - ingestStartTime
                                logger.error(
                                        ">>> ingestAlert failed after ${duration}ms: ${e.javaClass.simpleName}"
                                )
                                logger.error(">>> Error message: ${e.message}")
                                logger.error(">>> Stack trace:", e)
                                throw e
                        }

                val ingestDuration = System.currentTimeMillis() - ingestStartTime
                logger.info(">>> ingestAlert completed in ${ingestDuration}ms")
                logger.info(
                        ">>> Result: success=${result.success}, incidentId=${result.incidentId}"
                )
                assertTrue(result.success, "Alert ingestion should succeed")
                val incidentId = result.incidentId!!
                logger.info(">>> Incident ID: $incidentId")

                // 4. Verification: Wait for dice-server (real) to finish Embabel extraction
                // In a real environment, dice-server uses LLMs which take time
                logger.info(">>> Step 4: Waiting for DICE server to process propositions...")
                logger.info(">>> Waiting 3 seconds for LLM processing...")
                Thread.sleep(3000)
                logger.info(">>> Wait complete")

                // 5. Query the REAL DICE server for insights
                // This proves the two modules are communicating correctly
                logger.info(">>> Step 5: Querying DICE server for reasoning...")
                logger.info(
                        ">>> Question: 'What service is affected and what is the probable cause?'"
                )
                logger.info(
                        ">>> This will make HTTP POST to dice-server (may take 10-30 seconds)..."
                )
                val queryStartTime = System.currentTimeMillis()

                val diceMemory =
                        try {
                                diceClient.query(
                                        incidentId,
                                        "What service is affected and what is the probable cause?"
                                )
                        } catch (e: Exception) {
                                val duration = System.currentTimeMillis() - queryStartTime
                                logger.error(
                                        ">>> Query failed after ${duration}ms: ${e.javaClass.simpleName}"
                                )
                                logger.error(">>> Error message: ${e.message}")
                                logger.error(">>> Stack trace:", e)
                                throw e
                        }

                val queryDuration = System.currentTimeMillis() - queryStartTime
                logger.info(">>> Query completed in ${queryDuration}ms")
                logger.info(">>> DICE Reasoning Result (${diceMemory.length} chars):")
                logger.info(">>> $diceMemory")

                assertNotNull(diceMemory)
                assertTrue(diceMemory.contains("checkout-service", ignoreCase = true))
                logger.info(">>> Query result validation passed")

                // 6. Verify propositions were extracted in the real DICE server
                logger.info(">>> Step 6: Verifying propositions were extracted...")
                logger.info(">>> Calling diceClient.listPropositions($incidentId)...")
                val listStartTime = System.currentTimeMillis()

                val propositions =
                        try {
                                diceClient.listPropositions(incidentId)
                        } catch (e: Exception) {
                                val duration = System.currentTimeMillis() - listStartTime
                                logger.error(
                                        ">>> listPropositions failed after ${duration}ms: ${e.javaClass.simpleName}"
                                )
                                logger.error(">>> Error message: ${e.message}")
                                logger.error(">>> Stack trace:", e)
                                throw e
                        }

                val listDuration = System.currentTimeMillis() - listStartTime
                logger.info(">>> listPropositions completed in ${listDuration}ms")
                logger.info(">>> Propositions in DICE: ${propositions.size}")
                assertTrue(
                        propositions.isNotEmpty(),
                        "Real DICE server should have extracted propositions"
                )

                val totalDuration = System.currentTimeMillis() - testStartTime
                logger.info("")
                logger.info("========================================")
                logger.info(">>> TEST COMPLETE: SystemIntegrationTest")
                logger.info("========================================")
                logger.info("Total test duration: ${totalDuration}ms (${totalDuration / 1000.0}s)")
                logger.info("Test completed at: ${Instant.now()}")
                logger.info("")
        }

        companion object {
                @JvmStatic
                @AfterAll
                fun generateReports() {
                        // Generate test reports if any were collected
                        if (globalTestReportCollector.getReports().isNotEmpty()) {
                                globalTestReportCollector.generateReports("test-reports")
                        }
                }
        }
}
