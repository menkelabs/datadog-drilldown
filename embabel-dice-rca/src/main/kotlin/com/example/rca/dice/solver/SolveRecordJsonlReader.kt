package com.example.rca.dice.solver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/**
 * Reads one JSONL file; each non-blank line is a [SolveRecord].
 */
object SolveRecordJsonlReader {
    private val mapper = jacksonObjectMapper()

    fun readFile(path: Path): List<SolveRecord> {
        if (!path.isRegularFile()) return emptyList()
        return Files.readAllLines(path)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> mapper.readValue<SolveRecord>(line) }
            .toList()
    }

    /**
     * All `*.jsonl` files in a directory (sorted by name), concatenated in order.
     */
    fun readDirectory(dir: Path): List<SolveRecord> {
        if (!Files.isDirectory(dir)) return emptyList()
        val paths = Files.newDirectoryStream(dir, "*.jsonl").use { ds ->
            ds.toList().sortedBy { it.fileName.toString() }
        }
        return paths.flatMap { readFile(it) }
    }
}
