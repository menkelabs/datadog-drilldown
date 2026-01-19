package com.embabel.dice.service

import com.embabel.agent.api.annotation.Agent
import com.embabel.agent.api.annotation.Action
import com.embabel.dice.repository.PropositionRepository
import org.springframework.stereotype.Service

/**
 * Service to provide reasoning over stored propositions.
 * This implements the "R" in RCA via DICE memory.
 */
@Agent(name = "dice-reasoner")
interface DiceReasoner {

    @Action("Answer the user's question about an incident based only on the provided list of propositions. " +
            "If the propositions don't contain enough information, say so. " +
            "Focus on identifying root causes and potential remediation steps.")
    fun answerFromPropositions(question: String, propositions: List<String>): String
}

@Service
class ReasoningService(
    private val reasoner: DiceReasoner,
    private val repository: PropositionRepository
) {
    fun query(contextId: String, question: String): String {
        val propositions = repository.findByContext(contextId).map { it.text }
        if (propositions.isEmpty()) {
            return "No memory found for context $contextId. Please ingest data first."
        }
        
        return reasoner.answerFromPropositions(question, propositions)
    }
}
