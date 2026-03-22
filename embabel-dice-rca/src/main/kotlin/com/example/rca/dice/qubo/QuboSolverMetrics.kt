package com.example.rca.dice.qubo

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** Micrometer hooks for QUBO subprocess and enrichment outcomes (M2b / M2c D3). */
@Component
class QuboSolverMetrics(
    private val registry: MeterRegistry,
) {
    fun recordSubprocessDurationMs(durationMs: Long, outcome: String) {
        registry.timer("qubo.subprocess", "outcome", outcome).record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordEnrichment(outcome: String) {
        registry.counter("qubo.enrichment", "outcome", outcome).increment()
    }
}
