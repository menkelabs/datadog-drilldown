package com.example.testreport.api

import com.example.testreport.solver.SolverRunLineDto
import com.example.testreport.solver.SolverRunsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/solver-runs")
class SolverRunsApiController(
    private val solverRunsService: SolverRunsService,
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "500") limit: Int): List<SolverRunLineDto> =
        solverRunsService.listRecent(limit.coerceIn(1, 5000))
}
