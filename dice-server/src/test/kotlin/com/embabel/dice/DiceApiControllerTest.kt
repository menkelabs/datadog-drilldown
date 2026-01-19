package com.embabel.dice

import com.embabel.dice.api.DiceApiController
import com.embabel.dice.model.IngestRequest
import com.embabel.dice.model.SearchRequest
import com.embabel.dice.repository.PropositionRepository
import com.embabel.dice.service.KnowledgeService
import com.embabel.dice.service.ReasoningService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.Mockito.`when`
import com.embabel.dice.model.Proposition
import java.time.Instant

@WebMvcTest(DiceApiController::class)
class DiceApiControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var repository: PropositionRepository

    @MockBean
    private lateinit var knowledgeService: KnowledgeService

    @MockBean
    private lateinit var reasoningService: ReasoningService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `test ingest endpoint`() {
        val request = IngestRequest(documentId = "doc1", text = "This is a test.")
        `when`(knowledgeService.processAndStore("ctx1", request.text, request.documentId))
            .thenReturn(listOf(Proposition(contextId = "ctx1", text = "Fact 1")))

        mockMvc.perform(post("/api/v1/contexts/ctx1/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonExpectation("propositionsExtracted", 1))
    }

    @Test
    fun `test query endpoint`() {
        val request = mapOf("question" to "What happened?")
        `when`(reasoningService.query("ctx1", "What happened?"))
            .thenReturn("Something happened.")

        mockMvc.perform(post("/api/v1/contexts/ctx1/query")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk)
            .andExpect(jsonExpectation("answer", "Something happened."))
    }

    private fun jsonExpectation(path: String, value: Any) = jsonPath("$.$path").value(value)
}
