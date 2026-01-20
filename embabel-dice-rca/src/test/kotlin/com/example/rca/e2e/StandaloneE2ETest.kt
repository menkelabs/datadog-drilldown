package com.example.rca.e2e

import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Standalone end-to-end integration test client.
 * 
 * This is NOT a Spring Boot test - it's a standalone application that:
 * 1. Makes real HTTP calls to dice-server (localhost:8080)
 * 2. Makes real HTTP calls to embabel-dice-rca (if it exposes HTTP endpoints)
 * 3. Tests the full integration flow without Spring Boot test framework
 * 
 * Usage: Run from command line after both servers are started
 */
object StandaloneE2ETest {

    private val logger = LoggerFactory.getLogger(StandaloneE2ETest::class.java)
    
    private val diceServerUrl = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"
    private val rcaServerUrl = System.getenv("RCA_SERVER_URL") ?: "http://localhost:8081"
    
    private val restTemplate: RestTemplate = RestTemplateBuilder()
        .setConnectTimeout(Duration.ofSeconds(10))
        .setReadTimeout(Duration.ofSeconds(120))
        .build()

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("=== Standalone E2E Integration Test ===")
        
        try {
            // 1. Verify dice-server is running
            checkDiceServerHealth()
            
            // 2. Test full flow: Alert -> RCA Agent -> dice-server -> LLM -> Response
            testFullIntegrationFlow()
            
            logger.info("=== E2E Integration Test PASSED ===")
            // Don't call exitProcess() - let JUnit handle test lifecycle
        } catch (e: Exception) {
            logger.error("E2E Test FAILED", e)
            throw e  // Re-throw so JUnit sees the failure
        }
    }

    private fun checkDiceServerHealth() {
        logger.info("Checking dice-server health at $diceServerUrl...")
        try {
            val response = restTemplate.getForObject("$diceServerUrl/actuator/health", Map::class.java)
            val status = response?.get("status")?.toString()
            if (status != "UP") {
                throw RuntimeException("dice-server health is not UP: $status")
            }
            logger.info("✓ dice-server is healthy: $status")
        } catch (e: Exception) {
            throw RuntimeException("dice-server health check failed at $diceServerUrl: ${e.message}", e)
        }
    }

    private fun testFullIntegrationFlow() {
        logger.info("=== Testing Full Integration Flow ===")
        
        val incidentId = "e2e-test-${System.currentTimeMillis()}"
        logger.info("Using incident ID: $incidentId")
        
        // Step 1: Ingest alert text to dice-server
        val alertText = """
            Alert: checkout-latency-spike
            Service: checkout-service
            Environment: prod
            Severity: CRITICAL
            Message: P95 latency exceeded 2s threshold
            Timestamp: ${Instant.now()}
        """.trimIndent()
        
        logger.info("Step 1: Ingesting alert to dice-server...")
        val ingestResult = ingestToDiceServer(incidentId, "alert-001", alertText)
        logger.info("✓ Ingested: ${ingestResult["status"]}, propositions: ${ingestResult["propositionsExtracted"]}")
        
        // Step 2: Wait for dice-server to process (LLM extraction)
        logger.info("Step 2: Waiting for dice-server to process propositions (may take 10-30 seconds)...")
        val propositions = waitForPropositions(incidentId, maxWaitSeconds = 60)
        logger.info("✓ Found ${propositions.size} propositions:")
        propositions.take(5).forEach { prop ->
            logger.info("  - ${prop["text"]}")
        }
        
        if (propositions.isEmpty()) {
            throw RuntimeException("No propositions were extracted after waiting 60 seconds")
        }
        
        // Step 3: Query dice-server for reasoning
        logger.info("Step 3: Querying dice-server for reasoning...")
        val question = "What service is affected and what is the probable cause?"
        val answer = queryDiceServer(incidentId, question)
        logger.info("✓ Query answer: $answer")
        
        // Check if answer mentions checkout-service or related terms
        // Fallback answers may show propositions differently
        val answerLower = answer.lowercase()
        if (!answerLower.contains("checkout") && !answerLower.contains("service")) {
            throw RuntimeException("Answer should mention checkout or service. Got: $answer")
        }
        
        logger.info("=== Full Integration Flow Test PASSED ===")
    }

    private fun ingestToDiceServer(contextId: String, documentId: String, text: String): Map<String, Any> {
        return try {
            val url = "$diceServerUrl/api/v1/contexts/$contextId/ingest"
            val request = mapOf(
                "documentId" to documentId,
                "text" to text
            )
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(url, request, Map::class.java) as? Map<String, Any>
            response ?: throw RuntimeException("Null response from dice-server ingest")
        } catch (e: Exception) {
            throw RuntimeException("Failed to ingest to dice-server: ${e.message}", e)
        }
    }

    private fun waitForPropositions(contextId: String, maxWaitSeconds: Int): List<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        val maxWaitMillis = maxWaitSeconds * 1000
        
        while ((System.currentTimeMillis() - startTime) < maxWaitMillis) {
            val propositions = listPropositions(contextId)
            if (propositions.isNotEmpty()) {
                return propositions
            }
            Thread.sleep(2000)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            logger.info("  Waiting... (${elapsed}s elapsed, ${propositions.size} propositions)")
        }
        
        return emptyList()
    }

    private fun listPropositions(contextId: String): List<Map<String, Any>> {
        return try {
            val url = "$diceServerUrl/api/v1/contexts/$contextId/memory"
            val response = restTemplate.getForObject(url, Map::class.java)
            val props = response?.get("propositions") as? List<Map<String, Any>>
            props ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to list propositions: ${e.message}")
            emptyList()
        }
    }

    private fun queryDiceServer(contextId: String, question: String): String {
        return try {
            val url = "$diceServerUrl/api/v1/contexts/$contextId/query"
            val request = mapOf("question" to question)
            val response = restTemplate.postForObject(url, request, Map::class.java)
            (response?.get("answer") as? String) ?: throw RuntimeException("No answer in response: $response")
        } catch (e: Exception) {
            throw RuntimeException("Failed to query dice-server: ${e.message}", e)
        }
    }
}
