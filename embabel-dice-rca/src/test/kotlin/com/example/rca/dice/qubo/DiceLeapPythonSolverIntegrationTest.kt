package com.example.rca.dice.qubo

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Requires repo layout: DICE_LEAP_POC_ROOT points at `dice-leap-poc/` and Python deps installed (`pip install -e .`).
 */
@EnabledIfEnvironmentVariable(named = "DICE_LEAP_POC_ROOT", matches = ".+")
class DiceLeapPythonSolverIntegrationTest {

    @Test
    fun `solve_json produces SolveRecord for toy fixture`() {
        val root = System.getenv("DICE_LEAP_POC_ROOT")!!.trim()
        val toy = Path.of(root, "sample_data", "toy_dw_md.json")
        assertTrue(Files.isRegularFile(toy), "missing $toy")

        val props = QuboIntegrationProperties(
            enabled = true,
            diceLeapPocRoot = root,
            pythonExecutable = System.getenv("PYTHON")?.trim()?.ifEmpty { null } ?: "python3",
        )
        val solver = DiceLeapPythonSolver(props, QuboSolverMetrics(SimpleMeterRegistry()))
        val temp = Files.createTempFile("inst-", ".json")
        try {
            Files.copy(toy, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            val out = solver.solve(temp, forceStrategy = "qubo")
            when (val r = out.result) {
                is SolveResult.Failure -> throw AssertionError(r.message)
                is SolveResult.Success -> {
                    assertEquals("toy_dw_md", r.record.instanceId)
                    assertEquals("qubo", r.record.strategyChoice)
                    assertTrue(r.record.nVars > 0)
                    assertTrue(out.attemptsUsed >= 1)
                }
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
