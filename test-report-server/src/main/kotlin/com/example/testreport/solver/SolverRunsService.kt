package com.example.testreport.solver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.example.testreport.repository.SolverRunsRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Service
class SolverRunsService(
    private val objectMapper: ObjectMapper,
    @Value("\${solver-runs.directory:../dice-leap-poc/runs}") private val directory: String,
    private val solverRunsRepository: SolverRunsRepository,
) {

    /**
     * Reads all `*.jsonl` in [directory], sorted by filename, flattens lines,
     * returns the last [maxLines] non-blank records (most recent batch at end of list).
     */
    fun listFromJsonlDirectory(maxLines: Int): List<SolverRunLineDto> {
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

    /** Rows persisted in H2 (after [syncFromJsonlDirectory]). */
    fun listPersisted(maxLines: Int): List<SolverRunLineDto> =
        solverRunsRepository.findRecent(maxLines.coerceIn(1, 5000))

    /** Prefer DB when it has rows; otherwise read JSONL (no sync required for first view). */
    fun listAuto(maxLines: Int): List<SolverRunLineDto> {
        val lim = maxLines.coerceIn(1, 5000)
        if (solverRunsRepository.count() > 0) return listPersisted(lim)
        return listFromJsonlDirectory(lim)
    }

    /**
     * Replace all `solver_runs` rows with a full re-import from JSONL files (idempotent snapshot).
     */
    @Transactional
    fun syncFromJsonlDirectory(): Int {
        val dir = Paths.get(directory).toAbsolutePath().normalize()
        if (!Files.isDirectory(dir)) {
            solverRunsRepository.deleteAll()
            return 0
        }

        val lines = mutableListOf<String>()
        Files.newDirectoryStream(dir, "*.jsonl").use { ds ->
            ds.toList().sortedBy { it.fileName.toString() }.forEach { path ->
                Files.readAllLines(path, StandardCharsets.UTF_8).forEach { line ->
                    val t = line.trim()
                    if (t.isNotEmpty()) lines.add(t)
                }
            }
        }

        solverRunsRepository.deleteAll()
        var n = 0
        for (raw in lines) {
            val dto = objectMapper.readValue<SolverRunLineDto>(raw)
            solverRunsRepository.insert(dto.instanceId, raw)
            n++
        }
        return n
    }
}
