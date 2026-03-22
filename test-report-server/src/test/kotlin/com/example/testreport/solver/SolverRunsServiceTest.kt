package com.example.testreport.solver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SolverRunsServiceTest {

    @Test
    fun `listRecent reads jsonl from directory`(@TempDir dir: Path) {
        val json =
            """{"baseline_objective":0.0,"instance_id":"x","n_vars":1,"objective":-1.0,"runtime_ms":0.0,"selected_decisions":[],"solver_mode":"local_classical","strategy_choice":"heuristic_only","strategy_reason":"r","vs_baseline_delta":0.0}"""
        Files.writeString(dir.resolve("runs.jsonl"), json + "\n")

        val svc = SolverRunsService(jacksonObjectMapper(), dir.toString())
        val rows = svc.listRecent(10)
        assertEquals(1, rows.size)
        assertEquals("x", rows[0].instanceId)
        assertEquals("heuristic_only", rows[0].strategyChoice)
    }
}
