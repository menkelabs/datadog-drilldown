package com.example.rca.dice

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * REST client for the DICE server.
 * Talks to the dice-server module via HTTP.
 */
@Component
open class DiceClient(
    @Value("\${dice.server.url:http://localhost:8080}") private val diceServerUrl: String,
    restTemplateBuilder: RestTemplateBuilder = RestTemplateBuilder()
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // Configure RestTemplate with timeouts (120s for LLM calls)
    private val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(10))
        .setReadTimeout(Duration.ofSeconds(120))
        .build()

    /**
     * Ingest text into DICE for a specific context.
     */
    open fun ingest(contextId: String, documentId: String, text: String): IngestResponse {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/ingest"
        val request = IngestRequest(documentId = documentId, text = text)
        
        logger.info("Calling DICE ingest: url=$url, docId=$documentId, textLen=${text.length}")
        return try {
            val startTime = System.currentTimeMillis()
            val response = restTemplate.postForObject(url, request, IngestResponse::class.java)
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("DICE ingest completed in ${elapsed}ms: ${response?.status}")
            response ?: IngestResponse(documentId, 0, "ERROR", "Null response from DICE")
        } catch (e: Exception) {
            logger.error("Failed to ingest to DICE: ${e.message}", e)
            IngestResponse(documentId, 0, "ERROR", e.message)
        }
    }

    /**
     * Query DICE memory for an incident.
     */
    open fun query(contextId: String, question: String): String {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/query"
        val request = mapOf("question" to question)
        
        return try {
            val response = restTemplate.postForObject(url, request, Map::class.java)
            (response?.get("answer") as? String) ?: "No answer from DICE"
        } catch (e: Exception) {
            logger.error("Failed to query DICE: ${e.message}")
            "Error querying DICE: ${e.message}"
        }
    }

    /**
     * List propositions from DICE memory.
     */
    open fun listPropositions(contextId: String): List<DiceProposition> {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/memory"
        
        return try {
            val response = restTemplate.getForObject(url, Map::class.java)
            val props = response?.get("propositions") as? List<Map<String, Any>>
            props?.map { mapToProposition(it) } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Failed to list propositions from DICE: ${e.message}")
            emptyList()
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

// Simple DTOs for the client if they aren't already available in a shared model
data class IngestRequest(val documentId: String, val text: String)
data class IngestResponse(val documentId: String, val propositionsExtracted: Int, val status: String = "SUCCESS", val message: String? = null)
data class DiceProposition(val id: String, val text: String, val confidence: Double, val reasoning: String? = null)