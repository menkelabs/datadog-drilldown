package com.embabel.dice.service

import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.createObject
import com.embabel.agent.api.models.OpenAiModels
import com.embabel.dice.model.Proposition
import com.fasterxml.jackson.annotation.JsonClassDescription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Data class for extracted propositions from LLM.
 */
@JsonClassDescription("List of extracted propositions from text")
data class ExtractedPropositions(
    val propositions: List<String>
)

/**
 * Service to extract atomic semantic propositions from complex text.
 * Uses Embabel AI to perform LLM-based extraction.
 */
@Service
class KnowledgeService(
    private val repository: com.embabel.dice.repository.PropositionRepository,
    private val operationContext: OperationContext
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    /**
     * Process text into atomic propositions and store them.
     */
    fun processAndStore(contextId: String, text: String, documentId: String? = null): List<Proposition> {
        logger.info("Processing text for context $contextId, length=${text.length}")
        
        val strings = try {
            logger.debug("Calling LLM for proposition extraction...")
            val extracted: ExtractedPropositions = operationContext.ai()
                .withLlm(OpenAiModels.GPT_41_NANO)  // Use fast, cheap model
                .createObject(
                    """
                    Extract a list of atomic, self-contained factual propositions from the given text.
                    Each proposition should be a single sentence that captures a specific fact,
                    event, or observation about the system state or incident.
                    
                    Text to analyze:
                    $text
                    """.trimIndent()
                )
            logger.info("LLM extracted ${extracted.propositions.size} propositions")
            extracted.propositions
        } catch (e: Exception) {
            logger.warn("LLM extraction failed, using fallback: ${e.message}")
            // Fallback to simple split if LLM fails
            text.split(Regex("[.\\n]")).map { it.trim() }.filter { it.length > 15 }
        }

        return strings.map { str ->
            val prop = Proposition(
                contextId = contextId,
                text = str,
                confidence = 0.95, // High confidence for extracted facts
                sourceDocumentId = documentId
            )
            repository.save(prop)
        }
    }
}
