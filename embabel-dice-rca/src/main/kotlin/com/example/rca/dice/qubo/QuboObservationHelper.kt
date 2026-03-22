package com.example.rca.dice.qubo

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

/**
 * Wraps dice-leap-poc subprocess work in a Micrometer [Observation] (exports as OTel span when
 * `micrometer-tracing-bridge-otel` + OTLP are configured).
 */
@Component
class QuboObservationHelper(
    private val registry: ObservationRegistry,
) {
    fun <T> observePythonSolve(traceCaseId: String, block: () -> T): T {
        val safeId = traceCaseId.take(128)
        val observation = Observation.createNotStarted("qubo.python.solve", registry)
            .contextualName("qubo-python-solve")
            .lowCardinalityKeyValue(KeyValue.of("case.id", safeId))
            .start()
        return try {
            observation.openScope().use { block() }
        } finally {
            observation.stop()
        }
    }
}
