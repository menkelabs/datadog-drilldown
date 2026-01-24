package com.embabel.dice.repository

import com.embabel.dice.model.Proposition
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Repository

@Repository
class PropositionRepository {
    // contextId -> (propositionId -> Proposition)
    private val storage = ConcurrentHashMap<String, ConcurrentHashMap<String, Proposition>>()

    fun save(proposition: Proposition): Proposition {
        val contextMap = storage.getOrPut(proposition.contextId) { ConcurrentHashMap() }
        contextMap[proposition.id] = proposition
        return proposition
    }

    fun findById(contextId: String, id: String): Proposition? {
        return storage[contextId]?.get(id)
    }

    fun findByContext(contextId: String): List<Proposition> {
        return storage[contextId]?.values?.toList() ?: emptyList()
    }

    fun search(contextId: String, query: String, limit: Int): List<Proposition> {
        // Simple mock search: filter by text contains (case insensitive)
        return findByContext(contextId)
                .filter { it.text.contains(query, ignoreCase = true) }
                .take(limit)
    }

    fun delete(contextId: String, id: String): Boolean {
        return storage[contextId]?.remove(id) != null
    }

    /** Delete all propositions for a given context. */
    fun deleteContext(contextId: String): Boolean {
        return storage.remove(contextId) != null
    }

    /** Clear all stored propositions (useful for testing). */
    fun clearAll() {
        storage.clear()
    }

    /** Get count of contexts. */
    fun getContextCount(): Int {
        return storage.size
    }

    /** Get total proposition count across all contexts. */
    fun getTotalPropositionCount(): Int {
        return storage.values.sumOf { it.size }
    }
}
