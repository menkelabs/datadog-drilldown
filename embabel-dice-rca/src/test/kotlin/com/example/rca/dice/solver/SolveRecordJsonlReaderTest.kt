package com.example.rca.dice.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SolveRecordJsonlReaderTest {

    @Test
    fun `readFile parses dice-leap-poc SolveRecord json line`(@TempDir tempDir: Path) {
        val line =
            """
            {"baseline_objective":-5.0,"encoding_version":"1","instance_id":"toy","n_vars":2,
            "objective":-6.0,"runtime_ms":1.0,"selected_decisions":["A"],
            "solver_mode":"local_classical","strategy_choice":"qubo","strategy_reason":"test",
            "tier":"simple","vs_baseline_delta":1.0}
            """.trimIndent().replace("\n", "").trim()

        val f = tempDir.resolve("one.jsonl")
        Files.writeString(f, line + "\n")

        val rows = SolveRecordJsonlReader.readFile(f)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("toy", r.instanceId)
        assertEquals("local_classical", r.solverMode)
        assertEquals("qubo", r.strategyChoice)
        assertEquals(1.0, r.vsBaselineDelta, 1e-9)
        assertEquals("1", r.encodingVersion)
        assertEquals("simple", r.tier)
    }
}
