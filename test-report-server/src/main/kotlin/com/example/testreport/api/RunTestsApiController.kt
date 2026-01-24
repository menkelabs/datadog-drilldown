package com.example.testreport.api

import com.example.testreport.service.RunRequest
import com.example.testreport.service.RunResponse
import com.example.testreport.service.RunTestsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tests")
class RunTestsApiController(private val runTests: RunTestsService) {

    @PostMapping("/run")
    fun run(@RequestBody body: Map<String, Any>?): RunResponse {
        val pattern = (body?.get("pattern") as? String)?.trim().orEmpty()
        val verbose = body?.get("verbose") == true
        return runTests.startRun(RunRequest(pattern = pattern, verbose = verbose))
    }

    @GetMapping("/run/{runId}/status")
    fun runStatus(@PathVariable runId: String): ResponseEntity<Map<String, Any>> {
        val running = runTests.isRunning(runId)
        return ResponseEntity.ok(mapOf("runId" to runId, "running" to running))
    }

    @GetMapping("/run/{runId}/log")
    fun getLog(
            @PathVariable runId: String,
            @RequestParam(defaultValue = "500") tailLines: Int
    ): ResponseEntity<Map<String, Any>> {
        val content = runTests.getLogContent(runId, tailLines)
        return if (content != null) {
            ResponseEntity.ok(mapOf("runId" to runId, "content" to content, "hasMore" to true))
        } else {
            ResponseEntity.ok(mapOf("runId" to runId, "content" to "", "hasMore" to false))
        }
    }
}
