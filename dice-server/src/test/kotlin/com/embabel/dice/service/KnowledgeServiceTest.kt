package com.embabel.dice.service

import com.embabel.dice.model.Proposition
import com.embabel.dice.repository.PropositionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnowledgeServiceTest {

    private val repository: PropositionRepository = mock()
    private val extractor: PropositionExtractor = mock()
    private val service = KnowledgeService(extractor, repository)

    @Test
    fun `test processAndStore calls extractor and saves to repository`() {
        val text = "Incident detected. High latency."
        whenever(extractor.extractPropositions(text)).thenReturn(listOf("Fact 1", "Fact 2"))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Proposition }

        val result = service.processAndStore("ctx1", text, "doc1")

        assertEquals(2, result.size)
        assertEquals("Fact 1", result[0].text)
        assertEquals("Fact 2", result[1].text)
        verify(repository, times(2)).save(any())
    }

    @Test
    fun `test processAndStore fallback on extractor failure`() {
        val text = "This is a long sentence that should be split. And this is another one."
        whenever(extractor.extractPropositions(any())).thenThrow(RuntimeException("LLM error"))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as Proposition }

        val result = service.processAndStore("ctx1", text, "doc1")

        // Fallback splits by period and filters by length > 15
        assertTrue(result.isNotEmpty())
        verify(repository, atLeastOnce()).save(any())
    }
}

private fun assertTrue(condition: Boolean) = assert(condition)
