package com.example.rca.dice

import org.slf4j.LoggerFactory

/**
 * Helper utilities for cleaning up test data in dice-server.
 *
 * Usage in tests:
 * ```kotlin
 * @AfterEach
 * fun cleanup() {
 *     TestCleanupHelper.cleanupContext(diceClient, contextId)
 * }
 *
 * @AfterAll
 * fun cleanupAll() {
 *     TestCleanupHelper.cleanupAll(diceClient)
 * }
 * ```
 */
object TestCleanupHelper {
    private val logger = LoggerFactory.getLogger(TestCleanupHelper::class.java)

    /**
     * Delete all propositions for a specific context. Useful for cleaning up after individual
     * tests.
     */
    fun cleanupContext(diceClient: DiceClient, contextId: String) {
        try {
            logger.info("Cleaning up context: $contextId")
            val deleted = diceClient.deleteContext(contextId)
            if (deleted) {
                logger.info("Successfully deleted context: $contextId")
            } else {
                logger.warn("Failed to delete context: $contextId (may not exist)")
            }
        } catch (e: Exception) {
            logger.error("Error cleaning up context $contextId: ${e.message}", e)
        }
    }

    /**
     * Clear ALL contexts and propositions from dice-server. WARNING: This deletes all data! Use
     * with caution. Useful for cleaning up after test suites.
     */
    fun cleanupAll(diceClient: DiceClient) {
        try {
            logger.warn("Clearing ALL contexts and propositions from dice-server")
            val deleted = diceClient.clearAll()
            if (deleted) {
                logger.info("Successfully cleared all data from dice-server")
            } else {
                logger.warn("Failed to clear all data from dice-server")
            }
        } catch (e: Exception) {
            logger.error("Error clearing all data: ${e.message}", e)
        }
    }

    /** Cleanup multiple contexts at once. */
    fun cleanupContexts(diceClient: DiceClient, contextIds: List<String>) {
        logger.info("Cleaning up ${contextIds.size} contexts")
        contextIds.forEach { contextId -> cleanupContext(diceClient, contextId) }
    }
}
