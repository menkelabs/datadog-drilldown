package com.embabel.dice.repository

import com.embabel.dice.model.Proposition
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

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
}
