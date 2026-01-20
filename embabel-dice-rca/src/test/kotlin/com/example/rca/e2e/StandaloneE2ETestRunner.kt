package com.example.rca.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * JUnit wrapper to run StandaloneE2ETest via Maven.
 * This allows the standalone test to be run with mvn test.
 */
@EnabledIfEnvironmentVariable(named = "DICE_SERVER_URL", matches = ".*", disabledReason = "Set DICE_SERVER_URL env var to run E2E test")
class StandaloneE2ETestRunner {

    @Test
    fun `run standalone E2E test`() {
        StandaloneE2ETest.main(arrayOf())
    }
}
