package com.example.testreport.solver

import com.example.testreport.repository.SolverRunsRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.nio.file.Files
import java.nio.file.Path

class SolverRunsServiceTest {

    @Test
    fun `listFromJsonlDirectory reads jsonl from directory`(@TempDir dir: Path) {
        val json =
            """{"baseline_objective":0.0,"instance_id":"x","n_vars":1,"objective":-1.0,"runtime_ms":0.0,"selected_decisions":[],"solver_mode":"local_classical","strategy_choice":"heuristic_only","strategy_reason":"r","vs_baseline_delta":0.0}"""
        Files.writeString(dir.resolve("runs.jsonl"), json + "\n")

        val repo = mock(SolverRunsRepository::class.java)
        val svc = SolverRunsService(jacksonObjectMapper(), dir.toString(), repo)
        val rows = svc.listFromJsonlDirectory(10)
        assertEquals(1, rows.size)
        assertEquals("x", rows[0].instanceId)
    }

    @Test
    fun `syncFromJsonlDirectory clears and inserts`(@TempDir dir: Path) {
        val json =
            """{"baseline_objective":0.0,"instance_id":"z","n_vars":1,"objective":-1.0,"runtime_ms":0.0,"selected_decisions":[],"solver_mode":"local_classical","strategy_choice":"heuristic_only","strategy_reason":"r","vs_baseline_delta":0.0}"""
        Files.writeString(dir.resolve("a.jsonl"), json + "\n")

        val mapper = jacksonObjectMapper()
        val repo = mock(SolverRunsRepository::class.java)
        val svc = SolverRunsService(mapper, dir.toString(), repo)

        val n = svc.syncFromJsonlDirectory()
        assertEquals(1, n)
        verify(repo).deleteAll()
        verify(repo).insert(eq("z"), anyString())
    }
}
