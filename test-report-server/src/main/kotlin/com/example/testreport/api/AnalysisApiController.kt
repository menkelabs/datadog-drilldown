package com.example.testreport.api

import com.example.testreport.repository.AnalysisSummaryDto
import com.example.testreport.repository.TestRunRepository
import com.example.testreport.service.AnalysisSuggestionsService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class AnalysisApiController(
    private val repo: TestRunRepository,
    private val suggestionsService: AnalysisSuggestionsService,
) {

    @GetMapping("/analysis/summary")
    fun summary(): AnalysisSummaryDto? = repo.analysisSummary()

    @GetMapping("/analysis/suggestions")
    fun suggestions() = suggestionsService.getSuggestions()
}
