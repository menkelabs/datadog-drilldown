package com.embabel.dice.service

import com.embabel.agent.api.common.OperationContext
import com.embabel.agent.api.common.createObject
import com.embabel.agent.api.models.OpenAiModels
import com.embabel.dice.repository.PropositionRepository
import com.fasterxml.jackson.annotation.JsonClassDescription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Data class for LLM reasoning response.
 */
@JsonClassDescription("Answer from reasoning over propositions")
data class ReasoningAnswer(
    val answer: String
)

/**
 * Service to provide reasoning over stored propositions.
 * This implements the "R" in RCA via DICE memory.
 */
@Service
class ReasoningService(
    private val repository: PropositionRepository,
    private val operationContext: OperationContext
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    fun query(contextId: String, question: String): String {
        logger.info("Querying context $contextId: $question")
        
        val propositions = repository.findByContext(contextId).map { it.text }
        if (propositions.isEmpty()) {
            logger.warn("No propositions found for context $contextId")
            return "No memory found for context $contextId. Please ingest data first."
        }
        
        logger.debug("Found ${propositions.size} propositions, calling LLM for reasoning...")
        val propsText = propositions.joinToString("\n") { "- $it" }
        
        return try {
            val result: ReasoningAnswer = operationContext.ai()
                .withLlm(OpenAiModels.GPT_41_NANO)  // Use fast, cheap model
                .createObject(
                    """
                    Answer the user's question about an incident based only on the provided list of propositions.
                    If the propositions don't contain enough information, say so.
                    Focus on identifying root causes and potential remediation steps.
                    
                    Question: $question
                    
                    Known facts (propositions):
                    $propsText
                    """.trimIndent()
                )
            
            logger.info("LLM reasoning complete")
            result.answer
        } catch (e: Exception) {
            logger.error("LLM reasoning failed: ${e.message}", e)
            // Fallback: provide a simple answer based on propositions without LLM
            """
            Based on the ${propositions.size} propositions in memory for context $contextId:
            
            Question: $question
            
            Relevant propositions found:
            ${propositions.take(5).joinToString("\n") { "- $it" }}
            
            Note: LLM reasoning unavailable (${e.message}). Showing raw propositions instead.
            """.trimIndent()
        }
    }
}
