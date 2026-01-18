package com.example.rca.domain

/**
 * Represents a candidate root cause with scoring and evidence.
 * Maps to the Python dd_rca Candidate model.
 */
data class Candidate(
    val kind: CandidateKind,
    val title: String,
    val score: Double,
    val evidence: Map<String, Any> = emptyMap()
) {
    init {
        require(score in 0.0..1.0) { "Score must be between 0 and 1" }
    }
}

enum class CandidateKind {
    DEPENDENCY,       // Downstream service issue
    INFRASTRUCTURE,   // Infrastructure problem (DB, cache, etc.)
    CHANGE,          // Recent deployment or config change
    LOGS,            // Evidence from log patterns
    ENDPOINT,        // Endpoint-specific regression
    ERROR_PATTERN,   // Specific error pattern
    RESOURCE         // Resource exhaustion (CPU, memory, connections)
}

/**
 * Builder for creating candidates with evidence.
 */
class CandidateBuilder(
    private val kind: CandidateKind,
    private val title: String
) {
    private var score: Double = 0.5
    private val evidence = mutableMapOf<String, Any>()

    fun score(value: Double) = apply { this.score = value.coerceIn(0.0, 1.0) }
    
    fun evidence(key: String, value: Any) = apply { this.evidence[key] = value }
    
    fun evidence(map: Map<String, Any>) = apply { this.evidence.putAll(map) }

    fun build() = Candidate(kind, title, score, evidence.toMap())
}

fun candidate(kind: CandidateKind, title: String, init: CandidateBuilder.() -> Unit = {}): Candidate {
    return CandidateBuilder(kind, title).apply(init).build()
}
