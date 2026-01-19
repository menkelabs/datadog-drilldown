package com.example.rca.dice

import com.example.rca.dice.model.IngestRequest
import com.example.rca.dice.model.IngestResponse
import com.example.rca.model.Proposition
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.slf4j.LoggerFactory

/**
 * REST client for the DICE server.
 * Talks to the dice-server module via HTTP.
 */
@Component
class DiceClient(
    @Value("\${dice.server.url:http://localhost:8080}") private val diceServerUrl: String,
    private val restTemplate: RestTemplate = RestTemplate()
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Ingest text into DICE for a specific context.
     */
    fun ingest(contextId: String, documentId: String, text: String): IngestResponse {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/ingest"
        val request = IngestRequest(documentId = documentId, text = text)
        
        return try {
            val response = restTemplate.postForObject(url, request, IngestResponse::class.java)
            response ?: IngestResponse(documentId, 0, "ERROR", "Null response from DICE")
        } catch (e: Exception) {
            logger.error("Failed to ingest to DICE: ${e.message}")
            IngestResponse(documentId, 0, "ERROR", e.message)
        }
    }

    /**
     * Query DICE memory for an incident.
     */
    fun query(contextId: String, question: String): String {
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
    fun listPropositions(contextId: String): List<Proposition> {
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

    private fun mapToProposition(map: Map<String, Any>): Proposition {
        return Proposition(
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
