package com.example.rca.domain

import java.time.Instant

/**
 * Complete RCA analysis report.
 * Maps to the Python dd_rca Report model.
 */
data class Report(
    val meta: ReportMeta,
    val windows: Windows,
    val scope: Scope,
    val symptoms: List<Symptom>,
    val findings: Map<String, Any>,
    val recommendations: List<String>,
    val candidates: List<Candidate> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "meta" to meta.toMap(),
        "windows" to windows.toMap(),
        "scope" to scope.toMap(),
        "symptoms" to symptoms.map { symptomToMap(it) },
        "findings" to findings,
        "recommendations" to recommendations,
        "candidates" to candidates.map { candidateToMap(it) }
    )

    private fun symptomToMap(s: Symptom): Map<String, Any?> = mapOf(
        "type" to s.type.name.lowercase(),
        "query_or_signature" to s.queryOrSignature,
        "baseline_value" to s.baselineValue,
        "incident_value" to s.incidentValue,
        "percent_change" to s.percentChange,
        "peak_ts" to s.peakTs?.toString(),
        "peak_value" to s.peakValue
    )

    private fun candidateToMap(c: Candidate): Map<String, Any> = mapOf(
        "kind" to c.kind.name.lowercase(),
        "title" to c.title,
        "score" to c.score,
        "evidence" to c.evidence
    )
}

/**
 * Metadata about the report generation.
 */
data class ReportMeta(
    val seedType: SeedType,
    val generatedAt: Instant,
    val ddSite: String,
    val input: Map<String, Any>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "seed_type" to seedType.name.lowercase(),
        "generated_at" to generatedAt.toString(),
        "dd_site" to ddSite,
        "input" to input
    )
}

enum class SeedType {
    MONITOR,
    LOGS,
    SERVICE,
    ALERT,
    MANUAL
}

/**
 * Context for an ongoing incident investigation.
 */
data class IncidentContext(
    val id: String,
    val windows: Windows,
    val scope: Scope,
    val symptoms: MutableList<Symptom> = mutableListOf(),
    val candidates: MutableList<Candidate> = mutableListOf(),
    val chatHistory: MutableList<ChatMessage> = mutableListOf(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
) {
    fun addSymptom(symptom: Symptom) {
        symptoms.add(symptom)
    }

    fun addCandidate(candidate: Candidate) {
        candidates.add(candidate)
        candidates.sortByDescending { it.score }
    }

    fun addChatMessage(message: ChatMessage) {
        chatHistory.add(message)
    }

    fun topCandidates(limit: Int = 5): List<Candidate> =
        candidates.take(limit)
}

/**
 * Represents a chat message in the investigation.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestamp: Instant = Instant.now()
)

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}
