package com.example.testreport.api

import com.example.testreport.repository.AnalysisSummaryDto
import com.example.testreport.repository.TestRunRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class AnalysisApiController(private val repo: TestRunRepository) {

    @GetMapping("/analysis/summary")
    fun summary(): AnalysisSummaryDto? = repo.analysisSummary()
}
