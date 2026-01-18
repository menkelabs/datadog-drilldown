package com.example.rca.agent

import com.example.rca.domain.*
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Chat advisor for interactive incident investigation.
 * 
 * Provides natural language responses to questions about:
 * - Incident symptoms and severity
 * - Root cause candidates
 * - Log patterns and errors
 * - Remediation suggestions
 */
@Service
class ChatAdvisor {

    private val sessions = ConcurrentHashMap<String, ChatSession>()

    /**
     * Start a new chat session for an incident.
     */
    fun startSession(context: IncidentContext): ChatSession {
        val session = ChatSession(
            id = "chat-${context.id}",
            incidentId = context.id,
            startedAt = Instant.now()
        )
        sessions[session.id] = session

        // Add initial system context
        context.addChatMessage(ChatMessage(
            role = ChatRole.SYSTEM,
            content = buildSystemContext(context)
        ))

        return session
    }

    /**
     * Process a chat message and generate a response.
     */
    fun processMessage(message: String, context: IncidentContext): ChatResponse {
        // Add user message to history
        context.addChatMessage(ChatMessage(
            role = ChatRole.USER,
            content = message
        ))

        // Generate response based on message intent
        val intent = classifyIntent(message)
        val response = when (intent) {
            ChatIntent.SUMMARY -> generateSummary(context)
            ChatIntent.ROOT_CAUSE -> explainRootCauses(context)
            ChatIntent.LOGS -> explainLogPatterns(context)
            ChatIntent.REMEDIATION -> suggestRemediation(context)
            ChatIntent.TIMELINE -> explainTimeline(context)
            ChatIntent.SEVERITY -> explainSeverity(context)
            ChatIntent.DEPENDENCY -> explainDependencies(context)
            ChatIntent.GENERAL -> handleGeneralQuestion(message, context)
        }

        // Add assistant response to history
        context.addChatMessage(ChatMessage(
            role = ChatRole.ASSISTANT,
            content = response.message
        ))

        return response
    }

    /**
     * Get recommendations for an incident.
     */
    fun getRecommendations(context: IncidentContext): List<String> {
        val recommendations = mutableListOf<String>()

        // Based on top candidates
        context.topCandidates(3).forEach { candidate ->
            when (candidate.kind) {
                CandidateKind.DEPENDENCY -> {
                    val dep = candidate.evidence["dependency"]?.toString() ?: "downstream service"
                    recommendations.add("Check health and latency of $dep")
                }
                CandidateKind.ENDPOINT -> {
                    val endpoint = candidate.evidence["resource"]?.toString() ?: "affected endpoint"
                    recommendations.add("Investigate $endpoint for performance issues")
                }
                CandidateKind.LOGS -> {
                    recommendations.add("Review error logs matching: ${candidate.title.take(60)}")
                }
                CandidateKind.CHANGE -> {
                    recommendations.add("Consider rolling back recent deployment if timing correlates")
                }
                else -> {}
            }
        }

        // Based on symptoms
        context.symptoms.forEach { symptom ->
            when (symptom.type) {
                SymptomType.LATENCY -> {
                    if ((symptom.percentChange ?: 0.0) > 50) {
                        recommendations.add("Enable tracing for slow requests to identify bottlenecks")
                    }
                }
                SymptomType.ERROR_RATE -> {
                    recommendations.add("Check error logs for stack traces and error messages")
                }
                SymptomType.MEMORY -> {
                    recommendations.add("Analyze heap dumps and check for memory leaks")
                }
                else -> {}
            }
        }

        return recommendations.distinct().take(5)
    }

    // Private methods

    private fun classifyIntent(message: String): ChatIntent {
        val lower = message.lowercase()
        return when {
            lower.contains("summary") || lower.contains("what happened") || 
                lower.contains("overview") -> ChatIntent.SUMMARY
            lower.contains("root cause") || lower.contains("why") || 
                lower.contains("caused") -> ChatIntent.ROOT_CAUSE
            lower.contains("log") || lower.contains("error message") ||
                lower.contains("exception") -> ChatIntent.LOGS
            lower.contains("fix") || lower.contains("remediation") ||
                lower.contains("resolve") || lower.contains("how to") -> ChatIntent.REMEDIATION
            lower.contains("when") || lower.contains("timeline") ||
                lower.contains("started") -> ChatIntent.TIMELINE
            lower.contains("severity") || lower.contains("impact") ||
                lower.contains("how bad") -> ChatIntent.SEVERITY
            lower.contains("dependency") || lower.contains("downstream") ||
                lower.contains("upstream") -> ChatIntent.DEPENDENCY
            else -> ChatIntent.GENERAL
        }
    }

    private fun buildSystemContext(context: IncidentContext): String {
        return buildString {
            appendLine("Incident Analysis Context:")
            appendLine("- Incident ID: ${context.id}")
            appendLine("- Service: ${context.scope.service ?: "unknown"}")
            appendLine("- Environment: ${context.scope.env ?: "unknown"}")
            appendLine("- Incident Window: ${context.windows.incident.start} to ${context.windows.incident.end}")
            appendLine("- Symptoms detected: ${context.symptoms.size}")
            appendLine("- Candidates identified: ${context.candidates.size}")
        }
    }

    private fun generateSummary(context: IncidentContext): ChatResponse {
        val service = context.scope.service ?: "the affected service"
        val topCandidate = context.topCandidates(1).firstOrNull()

        val message = buildString {
            appendLine("**Incident Summary**")
            appendLine()
            appendLine("I analyzed data from ${context.windows.incident.start} to ${context.windows.incident.end}.")
            appendLine()

            if (context.symptoms.isNotEmpty()) {
                appendLine("**Symptoms detected:**")
                context.symptoms.take(3).forEach { symptom ->
                    val pctChange = symptom.percentChange?.let { "%.1f%%".format(it) } ?: "N/A"
                    appendLine("- ${symptom.type.name}: $pctChange change from baseline")
                }
                appendLine()
            }

            if (topCandidate != null) {
                appendLine("**Most likely root cause:**")
                appendLine("${topCandidate.title} (confidence: ${"%.0f%%".format(topCandidate.score * 100)})")
                appendLine()
            }

            appendLine("Ask me about specific details, remediation steps, or log patterns.")
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.SUMMARY,
            suggestions = listOf(
                "What caused this issue?",
                "Show me the error logs",
                "How do I fix this?"
            )
        )
    }

    private fun explainRootCauses(context: IncidentContext): ChatResponse {
        val candidates = context.topCandidates(5)

        val message = if (candidates.isEmpty()) {
            "I haven't identified any strong root cause candidates yet. This could mean:\n" +
            "- The data collected is insufficient\n" +
            "- The issue is intermittent\n" +
            "- More investigation is needed\n\n" +
            "Try asking about log patterns or timeline for more context."
        } else {
            buildString {
                appendLine("**Top Root Cause Candidates:**")
                appendLine()
                candidates.forEachIndexed { index, candidate ->
                    appendLine("${index + 1}. **${candidate.title}**")
                    appendLine("   - Type: ${candidate.kind.name}")
                    appendLine("   - Confidence: ${"%.0f%%".format(candidate.score * 100)}")
                    
                    // Add relevant evidence
                    candidate.evidence["dependency"]?.let {
                        appendLine("   - Dependency: $it")
                    }
                    candidate.evidence["resource"]?.let {
                        appendLine("   - Endpoint: $it")
                    }
                    appendLine()
                }
            }
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.ROOT_CAUSE,
            suggestions = listOf(
                "Tell me more about #1",
                "What should I check first?",
                "Show the evidence"
            )
        )
    }

    private fun explainLogPatterns(context: IncidentContext): ChatResponse {
        val logCandidates = context.candidates.filter { it.kind == CandidateKind.LOGS }

        val message = if (logCandidates.isEmpty()) {
            "No significant log patterns were identified during this incident. " +
            "This could indicate that the issue isn't manifesting as error logs, " +
            "or the error patterns are too varied to cluster."
        } else {
            buildString {
                appendLine("**Significant Log Patterns:**")
                appendLine()
                logCandidates.take(5).forEachIndexed { index, candidate ->
                    val template = candidate.evidence["template"]?.toString()?.take(100) ?: "Unknown pattern"
                    val incCount = candidate.evidence["count_incident"] ?: "?"
                    val baseCount = candidate.evidence["count_baseline"] ?: "?"
                    val isNew = candidate.evidence["is_new_pattern"] == true

                    appendLine("${index + 1}. `$template`")
                    appendLine("   - Count: $incCount during incident vs $baseCount baseline")
                    if (isNew) {
                        appendLine("   - **NEW PATTERN** (not seen in baseline)")
                    }
                    appendLine()
                }
            }
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.LOGS,
            suggestions = listOf(
                "Show full error stack",
                "Which service has the most errors?",
                "Are these errors new?"
            )
        )
    }

    private fun suggestRemediation(context: IncidentContext): ChatResponse {
        val recommendations = getRecommendations(context)

        val message = buildString {
            appendLine("**Recommended Actions:**")
            appendLine()
            recommendations.forEachIndexed { index, rec ->
                appendLine("${index + 1}. $rec")
            }
            appendLine()
            appendLine("Would you like more details on any of these recommendations?")
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.REMEDIATION,
            suggestions = listOf(
                "How do I implement #1?",
                "What's the priority order?",
                "Are there quick wins?"
            )
        )
    }

    private fun explainTimeline(context: IncidentContext): ChatResponse {
        val message = buildString {
            appendLine("**Incident Timeline:**")
            appendLine()
            appendLine("- **Baseline period**: ${context.windows.baseline.start} to ${context.windows.baseline.end}")
            appendLine("- **Incident period**: ${context.windows.incident.start} to ${context.windows.incident.end}")
            appendLine("- **Analysis anchor**: ${context.windows.anchor}")
            appendLine()

            val peakSymptom = context.symptoms.firstOrNull { it.peakTs != null }
            if (peakSymptom != null) {
                appendLine("**Peak observed at**: ${peakSymptom.peakTs}")
                appendLine("  - Type: ${peakSymptom.type.name}")
                appendLine("  - Peak value: ${peakSymptom.peakValue}")
            }
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.TIMELINE,
            suggestions = listOf(
                "What changed before this started?",
                "Were there any deploys?",
                "When did it recover?"
            )
        )
    }

    private fun explainSeverity(context: IncidentContext): ChatResponse {
        val maxSeverity = context.symptoms.maxOfOrNull { it.severity() } ?: Severity.UNKNOWN

        val message = buildString {
            appendLine("**Incident Severity Assessment:**")
            appendLine()
            appendLine("Overall severity: **${maxSeverity.name}**")
            appendLine()
            appendLine("Breakdown by symptom:")
            context.symptoms.forEach { symptom ->
                val severity = symptom.severity()
                val pctChange = symptom.percentChange?.let { "%.1f%%".format(it) } ?: "N/A"
                appendLine("- ${symptom.type.name}: $severity ($pctChange change)")
            }
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.SEVERITY,
            suggestions = listOf(
                "What's the business impact?",
                "Should we escalate?",
                "How many users affected?"
            )
        )
    }

    private fun explainDependencies(context: IncidentContext): ChatResponse {
        val depCandidates = context.candidates.filter { it.kind == CandidateKind.DEPENDENCY }

        val message = if (depCandidates.isEmpty()) {
            "No dependency issues were identified in the APM analysis. " +
            "Either dependencies are healthy, or APM data wasn't available for this service."
        } else {
            buildString {
                appendLine("**Dependency Analysis:**")
                appendLine()
                depCandidates.take(5).forEach { candidate ->
                    val dep = candidate.evidence["dependency"]?.toString() ?: "Unknown"
                    val durDelta = candidate.evidence["duration_delta_ms"]?.toString() ?: "?"
                    val errDelta = candidate.evidence["error_rate_delta"]?.toString() ?: "?"

                    appendLine("- **$dep**")
                    appendLine("  - Duration increase: ${durDelta}ms")
                    appendLine("  - Error rate change: $errDelta")
                    appendLine()
                }
            }
        }

        return ChatResponse(
            message = message,
            intent = ChatIntent.DEPENDENCY,
            suggestions = listOf(
                "Is the database slow?",
                "Check external API health",
                "Show dependency latencies"
            )
        )
    }

    private fun handleGeneralQuestion(message: String, context: IncidentContext): ChatResponse {
        return ChatResponse(
            message = "I can help you investigate this incident. Try asking about:\n" +
                "- **Summary**: \"What happened?\"\n" +
                "- **Root causes**: \"What caused this?\"\n" +
                "- **Logs**: \"Show me the errors\"\n" +
                "- **Remediation**: \"How do I fix this?\"\n" +
                "- **Timeline**: \"When did this start?\"\n" +
                "- **Severity**: \"How bad is this?\"",
            intent = ChatIntent.GENERAL,
            suggestions = listOf(
                "Give me a summary",
                "What's the root cause?",
                "How do I fix this?"
            )
        )
    }
}

/**
 * Chat session tracking.
 */
data class ChatSession(
    val id: String,
    val incidentId: String,
    val startedAt: Instant
)

/**
 * Response from the chat advisor.
 */
data class ChatResponse(
    val message: String,
    val intent: ChatIntent,
    val suggestions: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Classified intent of a chat message.
 */
enum class ChatIntent {
    SUMMARY,
    ROOT_CAUSE,
    LOGS,
    REMEDIATION,
    TIMELINE,
    SEVERITY,
    DEPENDENCY,
    GENERAL
}
