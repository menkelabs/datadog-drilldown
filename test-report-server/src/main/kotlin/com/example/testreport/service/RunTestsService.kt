package com.example.testreport.service

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

data class RunRequest(val pattern: String = "", val verbose: Boolean = false)

data class RunResponse(val runId: String, val status: String, val message: String)

/** Tracks active runs: runId -> Process. */
private val activeProcesses = ConcurrentHashMap<String, Process>()

/** Tracks log files: runId -> log file path. */
private val logFiles = ConcurrentHashMap<String, String>()

@Service
class RunTestsService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${run-tests.project-root:..}") private lateinit var projectRoot: String

    @Value("\${run-tests.script-path:}") private var scriptPathProp: String = ""

    /**
     * Start a test run asynchronously. The script [run-tests-with-server.sh] is executed with
     * [TEST_RUN_ID] and [TEST_REPORT_DB_PATH] set. Returns immediately with [runId]; poll [GET
     * /api/runs?runId=] for results.
     */
    fun startRun(request: RunRequest): RunResponse {
        val runId = "run-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        val root = File(projectRoot).absoluteFile
        val scriptFile =
                when {
                    scriptPathProp.isNotBlank() -> File(scriptPathProp).takeIf { it.isAbsolute }
                                    ?: File(root, scriptPathProp)
                    else -> File(root, "embabel-dice-rca/run-tests-with-server.sh")
                }
        if (!scriptFile.exists()) {
            log.error("Script not found: {}", scriptFile.absolutePath)
            return RunResponse(
                    runId,
                    "error",
                    "run-tests-with-server.sh not found at ${scriptFile.absolutePath}"
            )
        }
        val dbPath = File(root, "embabel-dice-rca/test-reports/test-history").absolutePath
        val pattern = request.pattern.trim().takeIf { it.isNotEmpty() } ?: ""
        val verbose = request.verbose
        val args = mutableListOf("bash", scriptFile.absolutePath)
        if (pattern.isNotEmpty()) args.add(pattern)
        if (verbose) args.add("--verbose")
        val logFile = File.createTempFile("test-run-$runId", ".log")
        val pb =
                ProcessBuilder(args)
                        .directory(root)
                        .redirectOutput(logFile)
                        .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
        // Preserve existing environment and add/override our vars
        pb.environment()["TEST_RUN_ID"] = runId
        pb.environment()["TEST_REPORT_DB_PATH"] = dbPath
        return try {
            val p = pb.start()
            activeProcesses[runId] = p
            logFiles[runId] = logFile.absolutePath
            Thread {
                        p.waitFor()
                        activeProcesses.remove(runId)
                        log.info(
                                "Test run finished: runId={} exitCode={} log={}",
                                runId,
                                p.exitValue(),
                                logFile.absolutePath
                        )
                    }
                    .apply { isDaemon = true }
                    .start()
            RunResponse(
                    runId,
                    "started",
                    "Test run started. Poll GET /api/runs?runId=$runId for results. Log: ${logFile.absolutePath}"
            )
        } catch (e: Exception) {
            log.error("Failed to start test run", e)
            RunResponse(runId, "error", e.message ?: "Failed to start")
        }
    }

    fun isRunning(runId: String): Boolean = activeProcesses.containsKey(runId)

    fun getLogPath(runId: String): String? = logFiles[runId]

    fun getLogContent(runId: String, tailLines: Int = 500): String? {
        val logPath = logFiles[runId] ?: return null
        val file = File(logPath)
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            val start = (lines.size - tailLines).coerceAtLeast(0)
            lines.subList(start, lines.size).joinToString("\n")
        } catch (e: Exception) {
            log.warn("Failed to read log file: {}", logPath, e)
            null
        }
    }
}
