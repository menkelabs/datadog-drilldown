package com.example.testreport.api

import com.example.testreport.solver.SolverRunLineDto
import com.example.testreport.solver.SolverRunsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class SolverRunsSyncResponse(val inserted: Int)

@RestController
@RequestMapping("/api/solver-runs")
class SolverRunsApiController(
    private val solverRunsService: SolverRunsService,
) {
    /**
     * @param source `db` (H2 only), `file` (JSONL only), `auto` (DB if non-empty else JSONL).
     */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "500") limit: Int,
        @RequestParam(defaultValue = "auto") source: String,
    ): List<SolverRunLineDto> {
        val lim = limit.coerceIn(1, 5000)
        return when (source.lowercase()) {
            "file" -> solverRunsService.listFromJsonlDirectory(lim)
            "db" -> solverRunsService.listPersisted(lim)
            else -> solverRunsService.listAuto(lim)
        }
    }

    /** Full re-import from [solver-runs.directory] into H2. */
    @PostMapping("/sync")
    fun sync(): SolverRunsSyncResponse =
        SolverRunsSyncResponse(inserted = solverRunsService.syncFromJsonlDirectory())
}
