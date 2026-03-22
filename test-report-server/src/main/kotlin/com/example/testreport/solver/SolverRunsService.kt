package com.example.testreport.solver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Service
class SolverRunsService(
    private val objectMapper: ObjectMapper,
    @Value("\${solver-runs.directory:../dice-leap-poc/runs}") private val directory: String,
) {

    /**
     * Reads all `*.jsonl` in [directory], sorted by filename, flattens lines,
     * returns the last [maxLines] non-blank records (most recent batch at end of list).
     */
    fun listRecent(maxLines: Int): List<SolverRunLineDto> {
        val dir = Paths.get(directory).toAbsolutePath().normalize()
        if (!Files.isDirectory(dir)) return emptyList()

        val lines = mutableListOf<String>()
        Files.newDirectoryStream(dir, "*.jsonl").use { ds ->
            ds.toList().sortedBy { it.fileName.toString() }.forEach { path ->
                Files.readAllLines(path, StandardCharsets.UTF_8).forEach { line ->
                    val t = line.trim()
                    if (t.isNotEmpty()) lines.add(t)
                }
            }
        }

        val tail = if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
        return tail.map { objectMapper.readValue<SolverRunLineDto>(it) }
    }
}
