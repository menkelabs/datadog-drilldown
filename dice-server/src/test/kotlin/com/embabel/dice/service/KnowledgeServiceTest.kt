package com.embabel.dice.service

import com.embabel.agent.api.common.OperationContext
import com.embabel.dice.model.Proposition
import com.embabel.dice.repository.PropositionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeServiceTest {

    private val repository: PropositionRepository = mock()
    private val operationContext: OperationContext = mock()
    private val service = KnowledgeService(repository, operationContext)

    @Test
    fun `test processAndStore fallback on LLM failure uses simple text splitting`() {
        // Long text that will be split by the fallback mechanism
        val text = "This is a long sentence that should be split properly. And this is another one that is also long."
        
        // Mock the OperationContext to throw an error to trigger fallback
        whenever(operationContext.ai()).thenThrow(RuntimeException("LLM error"))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Proposition }

        val result = service.processAndStore("ctx1", text, "doc1")

        // Fallback splits by period and filters by length > 15
        assertTrue(result.isNotEmpty(), "Should have at least one proposition from fallback")
        verify(repository, atLeastOnce()).save(any())
    }

    @Test
    fun `test processAndStore saves propositions with correct context and document id`() {
        val text = "This is a long enough sentence to pass the filter."
        
        // Mock to trigger fallback
        whenever(operationContext.ai()).thenThrow(RuntimeException("LLM error"))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Proposition }

        val result = service.processAndStore("ctx1", text, "doc1")

        // Verify saved propositions have correct metadata
        result.forEach { prop ->
            assertEquals("ctx1", prop.contextId)
            assertEquals("doc1", prop.sourceDocumentId)
            assertEquals(0.95, prop.confidence)
        }
    }
}
