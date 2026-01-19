package com.embabel.dice.service

import com.embabel.agent.api.Embabel
import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.Action
import com.embabel.dice.model.Proposition
import org.springframework.stereotype.Service
import java.util.*

/**
 * Service to extract atomic semantic propositions from complex text.
 * Uses Embabel AI Agent for extraction.
 */
@Agent(name = "proposition-extractor")
interface PropositionExtractor {

    @Action("Extract a list of atomic, self-contained factual propositions from the given text. " +
            "Each proposition should be a single sentence that captures a specific fact, " +
            "event, or observation about the system state or incident.")
    fun extractPropositions(text: String): List<String>
}

@Service
class KnowledgeService(
    private val extractor: PropositionExtractor,
    private val repository: com.embabel.dice.repository.PropositionRepository
) {
    /**
     * Process text into atomic propositions and store them.
     */
    fun processAndStore(contextId: String, text: String, documentId: String? = null): List<Proposition> {
        val strings = try {
            extractor.extractPropositions(text)
        } catch (e: Exception) {
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
