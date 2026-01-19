package com.embabel.dice.model

import java.time.Instant
import java.util.*

data class Proposition(
    val id: String = UUID.randomUUID().toString(),
    val contextId: String,
    val text: String,
    val confidence: Double = 1.0,
    val reasoning: String? = null,
    val status: String = "ACTIVE",
    val timestamp: Instant = Instant.now(),
    val metadata: Map<String, Any> = emptyMap(),
    val sourceDocumentId: String? = null
)

data class IngestRequest(
    val documentId: String,
    val text: String,
    val metadata: Map<String, Any> = emptyMap()
)

data class IngestResponse(
    val documentId: String,
    val propositionsExtracted: Int,
    val status: String = "SUCCESS",
    val message: String? = null
)

data class SearchRequest(
    val query: String,
    val topK: Int = 10,
    val similarityThreshold: Double = 0.7,
    val filters: Map<String, Any>? = null
)
