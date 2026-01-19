package com.embabel.dice.api

import com.embabel.dice.model.*
import com.embabel.dice.pipeline.PropositionPipeline
import com.embabel.dice.repository.PropositionRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/contexts/{contextId}")
class DiceApiController(
    private val repository: PropositionRepository,
    private val knowledgeService: com.embabel.dice.service.KnowledgeService,
    private val reasoningService: com.embabel.dice.service.ReasoningService
) {

    @PostMapping("/ingest")
    fun ingest(
        @PathVariable contextId: String,
        @RequestBody request: IngestRequest
    ): IngestResponse {
        val propositions = knowledgeService.processAndStore(contextId, request.text, request.documentId)
        
        return IngestResponse(
            documentId = request.documentId,
            propositionsExtracted = propositions.size
        )
    }

    @GetMapping("/memory")
    fun listPropositions(
        @PathVariable contextId: String,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "50") limit: Int
    ): Map<String, Any> {
        val props = repository.findByContext(contextId)
            .filter { status == null || it.status == status }
            .take(limit)
            
        return mapOf("propositions" to props)
    }

    @PostMapping("/memory/search")
    fun search(
        @PathVariable contextId: String,
        @RequestBody request: SearchRequest
    ): Map<String, Any> {
        val results = repository.search(contextId, request.query, request.topK)
        return mapOf("propositions" to results)
    }

    @GetMapping("/memory/{id}")
    fun getProposition(
        @PathVariable contextId: String,
        @PathVariable id: String
    ): Proposition? {
        return repository.findById(contextId, id)
    }

    @DeleteMapping("/memory/{id}")
    fun deleteProposition(
        @PathVariable contextId: String,
        @PathVariable id: String
    ) {
        repository.delete(contextId, id)
    }

    @PostMapping("/query")
    fun query(
        @PathVariable contextId: String,
        @RequestBody request: Map<String, String>
    ): Map<String, String> {
        val question = request["question"] ?: throw IllegalArgumentException("Missing 'question'")
        val answer = reasoningService.query(contextId, question)
        return mapOf("answer" to answer)
    }
}
