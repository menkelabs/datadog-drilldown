package com.example.testreport.api

import com.example.testreport.repository.TestRunRepository
import com.example.testreport.repository.TestRunDetailDto
import com.example.testreport.repository.TestRunRowDto
import com.example.testreport.repository.RunSummaryDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class RunsApiController(private val repo: TestRunRepository) {

    @GetMapping("/runs/summaries")
    fun runSummaries(@RequestParam(defaultValue = "20") limit: Int): List<RunSummaryDto> =
        repo.runSummaries(limit.coerceIn(1, 100))

    @GetMapping("/runs")
    fun list(
        @RequestParam(required = false) runId: String?,
        @RequestParam(required = false) scenarioId: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<TestRunRowDto> = repo.list(runId, scenarioId, status, limit.coerceIn(1, 500))

    @GetMapping("/runs/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<TestRunDetailDto> {
        val run = repo.runById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(run)
    }

    @GetMapping("/runs/runId/{runId}")
    fun getByRunId(@PathVariable runId: String): List<TestRunRowDto> =
        repo.runsByRunId(runId)
}
