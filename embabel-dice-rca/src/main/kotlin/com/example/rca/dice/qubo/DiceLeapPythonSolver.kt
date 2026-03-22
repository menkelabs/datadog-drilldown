package com.example.rca.dice.qubo

import com.example.rca.dice.solver.SolveRecord
import com.example.rca.dice.solver.SolveRecordJsonlReader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Invokes [dice-leap-poc/scripts/solve_json.py] with PYTHONPATH set to the package root.
 * Retries up to [QuboIntegrationProperties.maxSubprocessAttempts] (M2c).
 */
@Component
class DiceLeapPythonSolver(
    private val properties: QuboIntegrationProperties,
    private val metrics: QuboSolverMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun solve(instanceJsonFile: Path, forceStrategy: String? = null): SolveWithAttempts {
        val attempts = max(1, properties.maxSubprocessAttempts)
        var last: SolveResult = SolveResult.Failure("no attempts")
        var totalMs = 0L
        repeat(attempts) { idx ->
            val (ms, result) = solveOnce(instanceJsonFile, forceStrategy)
            totalMs += ms
            val tag = if (result is SolveResult.Success) "success" else "failure"
            metrics.recordSubprocessDurationMs(ms, tag)
            if (result is SolveResult.Success) {
                return SolveWithAttempts(result, idx + 1, totalMs)
            }
            last = result
            if (idx < attempts - 1 && properties.subprocessRetryDelayMs > 0) {
                try {
                    Thread.sleep(properties.subprocessRetryDelayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return SolveWithAttempts(last, idx + 1, totalMs)
                }
            }
        }
        return SolveWithAttempts(last, attempts, totalMs)
    }

    private fun solveOnce(instanceJsonFile: Path, forceStrategy: String?): Pair<Long, SolveResult> {
        val t0 = System.nanoTime()
        fun done(r: SolveResult): Pair<Long, SolveResult> {
            val ms = (System.nanoTime() - t0) / 1_000_000L
            return ms to r
        }

        val root = properties.diceLeapPocRoot.trim()
        if (root.isEmpty()) {
            return done(SolveResult.Failure("embabel.rca.qubo.dice-leap-poc-root is not set"))
        }
        val rootDir = File(root)
        if (!rootDir.isDirectory) {
            return done(SolveResult.Failure("dice-leap-poc root is not a directory: $root"))
        }
        val script = File(rootDir, "scripts/solve_json.py")
        if (!script.isFile) {
            return done(SolveResult.Failure("solve_json.py not found under $root/scripts/"))
        }

        val cmd = mutableListOf(
            properties.pythonExecutable,
            script.absolutePath,
            "--input",
            instanceJsonFile.toAbsolutePath().toString(),
            "--solver-mode",
            "local_classical",
        )
        if (forceStrategy != null) {
            cmd.add("--strategy-choice")
            cmd.add(forceStrategy)
        }

        val pb = ProcessBuilder(cmd)
        pb.directory(rootDir)
        pb.environment()["PYTHONPATH"] = rootDir.absolutePath
        pb.redirectErrorStream(true)

        return try {
            val proc = pb.start()
            val out = proc.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            val finished = proc.waitFor(properties.subprocessTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return done(SolveResult.Failure("subprocess timed out after ${properties.subprocessTimeoutSeconds}s"))
            }
            if (proc.exitValue() != 0) {
                return done(SolveResult.Failure("exit=${proc.exitValue()} output=${out.take(2000)}"))
            }
            val line = out.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?: return done(SolveResult.Failure("no JSON line on stdout"))
            done(SolveResult.Success(SolveRecordJsonlReader.parseLine(line)))
        } catch (e: Exception) {
            log.warn("dice-leap-poc subprocess failed", e)
            done(SolveResult.Failure(e.message ?: e.javaClass.simpleName))
        }
    }
}

sealed class SolveResult {
    data class Success(val record: SolveRecord) : SolveResult()
    data class Failure(val message: String) : SolveResult()
}

data class SolveWithAttempts(
    val result: SolveResult,
    val attemptsUsed: Int,
    /** Sum of wall times for each subprocess attempt (ms). */
    val totalSubprocessMs: Long = 0L,
)
