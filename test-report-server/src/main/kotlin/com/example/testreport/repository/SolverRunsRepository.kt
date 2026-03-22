package com.example.testreport.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.example.testreport.solver.SolverRunLineDto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SolverRunsRepository(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun deleteAll(): Int = jdbc.update("DELETE FROM solver_runs")

    fun insert(instanceId: String, rawJson: String) {
        jdbc.update(
            "INSERT INTO solver_runs (instance_id, raw_json) VALUES (?, ?)",
            instanceId,
            rawJson,
        )
    }

    fun count(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM solver_runs", Long::class.java) ?: 0L

    fun findRecent(limit: Int): List<SolverRunLineDto> {
        val sql = "SELECT raw_json FROM solver_runs ORDER BY id DESC LIMIT ?"
        return jdbc.query(sql, { rs, _ ->
            objectMapper.readValue<SolverRunLineDto>(rs.getString("raw_json"))
        }, limit)
    }
}
