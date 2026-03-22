package com.example.rca.dice.qubo

import com.example.rca.dice.solver.SolveRecord
import com.example.rca.dice.solver.SolveRecordJsonlReader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Invokes [dice-leap-poc/scripts/solve_json.py] with PYTHONPATH set to the package root.
 */
@Component
class DiceLeapPythonSolver(
    private val properties: QuboIntegrationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun solve(instanceJsonFile: Path, forceStrategy: String? = null): SolveResult {
        val root = properties.diceLeapPocRoot.trim()
        if (root.isEmpty()) {
            return SolveResult.Failure("embabel.rca.qubo.dice-leap-poc-root is not set")
        }
        val rootDir = File(root)
        if (!rootDir.isDirectory) {
            return SolveResult.Failure("dice-leap-poc root is not a directory: $root")
        }
        val script = File(rootDir, "scripts/solve_json.py")
        if (!script.isFile) {
            return SolveResult.Failure("solve_json.py not found under $root/scripts/")
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
                return SolveResult.Failure("subprocess timed out after ${properties.subprocessTimeoutSeconds}s")
            }
            if (proc.exitValue() != 0) {
                return SolveResult.Failure("exit=${proc.exitValue()} output=${out.take(2000)}")
            }
            val line = out.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
                ?: return SolveResult.Failure("no JSON line on stdout")
            SolveResult.Success(SolveRecordJsonlReader.parseLine(line))
        } catch (e: Exception) {
            log.warn("dice-leap-poc subprocess failed", e)
            SolveResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }
}

sealed class SolveResult {
    data class Success(val record: SolveRecord) : SolveResult()
    data class Failure(val message: String) : SolveResult()
}
