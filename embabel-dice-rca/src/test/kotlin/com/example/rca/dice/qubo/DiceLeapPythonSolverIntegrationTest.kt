package com.example.rca.dice.qubo

import com.example.rca.dice.qubo.QuboObservationHelper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Requires repo layout: `DICE_LEAP_POC_ROOT` points at `dice-leap-poc/` and Python deps installed (`pip install -e .`).
 * Interpreter resolution: `QUBO_PYTHON_EXECUTABLE`, then `PYTHON`, then `$DICE_LEAP_POC_ROOT/.venv/bin/python` if executable, else `python3`.
 */
@EnabledIfEnvironmentVariable(named = "DICE_LEAP_POC_ROOT", matches = ".+")
class DiceLeapPythonSolverIntegrationTest {

    @Test
    fun `solve_json produces SolveRecord for toy fixture`() {
        val root = System.getenv("DICE_LEAP_POC_ROOT")!!.trim()
        val toy = Path.of(root, "sample_data", "toy_dw_md.json")
        assertTrue(Files.isRegularFile(toy), "missing $toy")

        val pyExe = resolvePythonForDiceLeapPoc(root)
        val props = QuboIntegrationProperties(
            enabled = true,
            diceLeapPocRoot = root,
            pythonExecutable = pyExe,
        )
        val solver = DiceLeapPythonSolver(
            props,
            QuboSolverMetrics(SimpleMeterRegistry()),
            QuboObservationHelper(ObservationRegistry.create()),
        )
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

    companion object {
        fun resolvePythonForDiceLeapPoc(root: String): String {
            fun envNonBlank(name: String): String? =
                System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

            envNonBlank("QUBO_PYTHON_EXECUTABLE")?.let { return it }
            envNonBlank("PYTHON")?.let { return it }

            val venvPy = Path.of(root, ".venv", "bin", "python")
            if (venvPy.isExecutable()) {
                return venvPy.toAbsolutePath().toString()
            }

            return "python3"
        }
    }
}
