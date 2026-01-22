package com.example.rca.integration

import com.example.rca.agent.RcaAgent
import com.example.rca.dice.DiceClient
import com.example.rca.dice.DiceIngestionService
import com.example.rca.dice.model.AlertSeverity
import com.example.rca.dice.model.AlertTrigger
import com.example.rca.dice.model.AlertType
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

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
 * To run: Start dice-server, set OPENAI_API_KEY, then remove @Disabled
 */
@Disabled("Requires running dice-server and OpenAI API key")
@SpringBootTest
@ActiveProfiles("test")
class SystemIntegrationTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    lateinit var diceIngestionService: DiceIngestionService

    @Autowired
    lateinit var diceClient: DiceClient

    @Autowired
    lateinit var rcaAgent: RcaAgent

    @Autowired
    lateinit var datadogClient: MockDatadogClient

    @Test
    fun `test full integration from alert to dice query`() {
        logger.info(">>> TEST START: Setting up scenario")
        
        // 1. Setup mock Datadog scenario (Fake Datadog data via Interface)
        val incidentStart = Instant.now().minusSeconds(600)
        val testScenario = scenario("checkout-failure-scenario") {
            description("Checkout service failing due to DB timeout")
            incidentStart(incidentStart)
            // Add fake data points that the agent will analyze
        }
        logger.info(">>> Scenario created, loading into datadogClient")
        datadogClient.loadScenario("checkout-failure-scenario", testScenario)
        datadogClient.setActiveScenario("checkout-failure-scenario")
        logger.info(">>> Scenario loaded, creating trigger")

        // 2. Trigger an alert (Fake Event from Datadog)
        val trigger = AlertTrigger(
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
        logger.info(">>> CALLING diceIngestionService.ingestAlert()")
        val result = diceIngestionService.ingestAlert(trigger)
        logger.info(">>> ingestAlert returned: success=${result.success}, incidentId=${result.incidentId}")
        assertTrue(result.success, "Alert ingestion should succeed")
        val incidentId = result.incidentId!!

        // 4. Verification: Wait for dice-server (real) to finish Embabel extraction
        // In a real environment, dice-server uses LLMs which take time
        println("Waiting for DICE server to process propositions...")
        Thread.sleep(3000) 

        // 5. Query the REAL DICE server for insights
        // This proves the two modules are communicating correctly
        val diceMemory = diceClient.query(incidentId, "What service is affected and what is the probable cause?")
        println("DICE Reasoning Result: $diceMemory")
        
        assertNotNull(diceMemory)
        assertTrue(diceMemory.contains("checkout-service", ignoreCase = true))
        
        // 6. Verify propositions were extracted in the real DICE server
        val propositions = diceClient.listPropositions(incidentId)
        println("Propositions in DICE: ${propositions.size}")
        assertTrue(propositions.isNotEmpty(), "Real DICE server should have extracted propositions")
    }
}
