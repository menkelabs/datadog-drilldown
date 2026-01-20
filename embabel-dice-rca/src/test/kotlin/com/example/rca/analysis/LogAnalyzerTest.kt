package com.example.rca.analysis

import com.example.rca.datadog.dto.LogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class LogAnalyzerTest {

    private val analyzer = LogAnalyzer()

    @Test
    fun `test clustering logs by fingerprint`() {
        val now = Instant.now()
        val logs = listOf(
            LogEntry(id = "1", timestamp = now, message = "Error connecting to DB", attributes = mapOf("service" to "api")),
            LogEntry(id = "2", timestamp = now.plusSeconds(1), message = "Error connecting to DB", attributes = mapOf("service" to "api")),
            LogEntry(id = "3", timestamp = now.plusSeconds(2), message = "Out of memory", attributes = mapOf("service" to "api"))
        )

        val clusters = analyzer.clusterLogs(logs)

        assertEquals(2, clusters.size)
        val dbCluster = clusters.find { it.template.contains("DB") }
        assertEquals(2, dbCluster?.countIncident)
        val oomCluster = clusters.find { it.template.contains("memory") }
        assertEquals(1, oomCluster?.countIncident)
    }

    @Test
    fun `test anomaly scoring`() {
        val now = Instant.now()
        val cluster = analyzer.clusterLogs(listOf(
            LogEntry(id = "1", timestamp = now, message = "New Error", attributes = emptyMap())
        )).first().copy(countBaseline = 0)

        // Score for new pattern should be high
        assertTrue(cluster.anomalyScore() > 0)
    }
}

private fun assertTrue(condition: Boolean) = assert(condition)
