package com.example.rca.dice

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * REST client for the DICE server.
 * 
 * Matches the DICE server API at dice-server/src/main/kotlin/com/embabel/dice/api/DiceApiController.kt:
 * - POST /api/v1/contexts/{contextId}/ingest - Ingest text and extract propositions
 * - GET  /api/v1/contexts/{contextId}/memory - List all propositions
 * - POST /api/v1/contexts/{contextId}/memory/search - Semantic search propositions
 * - GET  /api/v1/contexts/{contextId}/memory/{id} - Get single proposition
 * - DELETE /api/v1/contexts/{contextId}/memory/{id} - Delete proposition
 * - POST /api/v1/contexts/{contextId}/query - Query for reasoning/answer
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
     * 
     * API: POST /api/v1/contexts/{contextId}/ingest
     * Request: IngestRequest(documentId, text, metadata?)
     * Response: IngestResponse(documentId, propositionsExtracted, status, message?)
     */
    open fun ingest(contextId: String, documentId: String, text: String, metadata: Map<String, Any> = emptyMap()): IngestResponse {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/ingest"
        val request = IngestRequest(documentId = documentId, text = text, metadata = metadata)
        
        logger.info("DICE ingest: contextId=$contextId, docId=$documentId, textLen=${text.length}")
        return try {
            val startTime = System.currentTimeMillis()
            val response = restTemplate.postForObject(url, request, IngestResponse::class.java)
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("DICE ingest completed in ${elapsed}ms: ${response?.status}, propositions=${response?.propositionsExtracted}")
            response ?: IngestResponse(documentId, 0, "ERROR", "Null response from DICE")
        } catch (e: Exception) {
            logger.error("Failed to ingest to DICE: ${e.message}", e)
            IngestResponse(documentId, 0, "ERROR", e.message)
        }
    }

    /**
     * Query DICE for reasoning/answer based on stored knowledge.
     * 
     * API: POST /api/v1/contexts/{contextId}/query
     * Request: { "question": "..." }
     * Response: { "answer": "..." }
     */
    open fun query(contextId: String, question: String): String {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/query"
        val request = mapOf("question" to question)
        
        logger.info("DICE query: contextId=$contextId, question=${question.take(100)}...")
        return try {
            val startTime = System.currentTimeMillis()
            val response = restTemplate.postForObject(url, request, Map::class.java)
            val elapsed = System.currentTimeMillis() - startTime
            val answer = (response?.get("answer") as? String) ?: "No answer from DICE"
            logger.info("DICE query completed in ${elapsed}ms, answerLen=${answer.length}")
            answer
        } catch (e: Exception) {
            logger.error("Failed to query DICE: ${e.message}")
            "Error querying DICE: ${e.message}"
        }
    }

    /**
     * List all propositions from DICE memory for a context.
     * 
     * API: GET /api/v1/contexts/{contextId}/memory
     * Query params: status? (filter), limit (default 50)
     * Response: { "propositions": [...] }
     */
    open fun listPropositions(contextId: String, status: String? = null, limit: Int = 50): List<DiceProposition> {
        var url = "$diceServerUrl/api/v1/contexts/$contextId/memory?limit=$limit"
        if (status != null) url += "&status=$status"
        
        logger.info("DICE listPropositions: contextId=$contextId, status=$status, limit=$limit")
        return try {
            val response = restTemplate.getForObject(url, Map::class.java)
            val props = response?.get("propositions") as? List<Map<String, Any>>
            val result = props?.map { mapToProposition(it) } ?: emptyList()
            logger.info("DICE listPropositions returned ${result.size} propositions")
            result
        } catch (e: Exception) {
            logger.error("Failed to list propositions from DICE: ${e.message}")
            emptyList()
        }
    }

    /**
     * Semantic search for relevant propositions.
     * 
     * API: POST /api/v1/contexts/{contextId}/memory/search
     * Request: SearchRequest(query, topK, similarityThreshold?, filters?)
     * Response: { "propositions": [...] }
     */
    open fun searchPropositions(
        contextId: String,
        query: String,
        topK: Int = 10,
        similarityThreshold: Double = 0.7,
        filters: Map<String, Any>? = null
    ): List<DiceProposition> {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/memory/search"
        val request = SearchRequest(
            query = query,
            topK = topK,
            similarityThreshold = similarityThreshold,
            filters = filters
        )
        
        logger.info("DICE search: contextId=$contextId, query=${query.take(50)}..., topK=$topK")
        return try {
            val response = restTemplate.postForObject(url, request, Map::class.java)
            val props = response?.get("propositions") as? List<Map<String, Any>>
            val result = props?.map { mapToProposition(it) } ?: emptyList()
            logger.info("DICE search returned ${result.size} propositions")
            result
        } catch (e: Exception) {
            logger.error("Failed to search DICE: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get a single proposition by ID.
     * 
     * API: GET /api/v1/contexts/{contextId}/memory/{id}
     * Response: Proposition or null
     */
    open fun getProposition(contextId: String, propositionId: String): DiceProposition? {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/memory/$propositionId"
        
        return try {
            val response = restTemplate.getForObject(url, Map::class.java)
            response?.let { mapToProposition(it as Map<String, Any>) }
        } catch (e: Exception) {
            logger.error("Failed to get proposition from DICE: ${e.message}")
            null
        }
    }

    /**
     * Delete a proposition from DICE memory.
     * 
     * API: DELETE /api/v1/contexts/{contextId}/memory/{id}
     */
    open fun deleteProposition(contextId: String, propositionId: String): Boolean {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/memory/$propositionId"
        
        return try {
            restTemplate.delete(url)
            logger.info("DICE deleted proposition: $propositionId")
            true
        } catch (e: Exception) {
            logger.error("Failed to delete proposition from DICE: ${e.message}")
            false
        }
    }
    
    /**
     * Delete all propositions for a context.
     * 
     * API: DELETE /api/v1/contexts/{contextId}/memory
     */
    open fun deleteContext(contextId: String): Boolean {
        val url = "$diceServerUrl/api/v1/contexts/$contextId/memory"
        
        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                null,
                Map::class.java
            )
            val result = response.body as? Map<*, *>
            logger.info("DICE deleted context: $contextId, result: $result")
            true
        } catch (e: Exception) {
            logger.error("Failed to delete context from DICE: ${e.message}")
            false
        }
    }
    
    /**
     * Clear all contexts and propositions (useful for testing).
     * 
     * API: DELETE /api/v1/contexts
     * WARNING: This deletes ALL data in dice-server!
     */
    open fun clearAll(): Boolean {
        val url = "$diceServerUrl/api/v1/contexts"
        
        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                null,
                Map::class.java
            )
            val result = response.body as? Map<*, *>
            logger.warn("DICE cleared all contexts and propositions: $result")
            true
        } catch (e: Exception) {
            logger.error("Failed to clear all from DICE: ${e.message}")
            false
        }
    }

    private fun mapToProposition(map: Map<String, Any>): DiceProposition {
        return DiceProposition(
            id = map["id"] as? String ?: "",
            contextId = map["contextId"] as? String ?: "",
            text = map["text"] as? String ?: "",
            confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0,
            reasoning = map["reasoning"] as? String,
            status = map["status"] as? String ?: "ACTIVE",
            timestamp = (map["timestamp"] as? String)?.let { Instant.parse(it) } ?: Instant.now(),
            sourceDocumentId = map["sourceDocumentId"] as? String
        )
    }
}

/**
 * Request to ingest text into DICE.
 * Matches dice-server IngestRequest.
 */
data class IngestRequest(
    val documentId: String,
    val text: String,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Response from DICE ingest operation.
 * Matches dice-server IngestResponse.
 */
data class IngestResponse(
    val documentId: String,
    val propositionsExtracted: Int,
    val status: String = "SUCCESS",
    val message: String? = null
)

/**
 * Request for semantic search in DICE memory.
 * Matches dice-server SearchRequest.
 */
data class SearchRequest(
    val query: String,
    val topK: Int = 10,
    val similarityThreshold: Double = 0.7,
    val filters: Map<String, Any>? = null
)

/**
 * A proposition stored in DICE memory.
 * Matches dice-server Proposition.
 */
data class DiceProposition(
    val id: String,
    val contextId: String = "",
    val text: String,
    val confidence: Double,
    val reasoning: String? = null,
    val status: String = "ACTIVE",
    val timestamp: Instant = Instant.now(),
    val sourceDocumentId: String? = null
)