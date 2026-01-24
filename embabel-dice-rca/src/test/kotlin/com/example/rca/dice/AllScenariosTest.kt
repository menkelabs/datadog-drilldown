package com.example.rca.dice

import com.example.rca.datadog.dto.*
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.slf4j.LoggerFactory

/**
 * Comprehensive test suite covering ALL scenario files.
 *
 * Each test:
 * 1. Loads prior knowledge relevant to the scenario
 * 2. Sets up mock Datadog data from the scenario
 * 3. Simulates the alert (symptom only)
 * 4. Verifies the RCA reaches the correct root cause
 */
class AllScenariosTest {

        private val logger = LoggerFactory.getLogger(javaClass)
        private lateinit var diceClient: DiceClient
        private lateinit var mockDatadog: MockDatadogClient
        private lateinit var testFramework: DiceKnowledgeTestFramework

        @BeforeEach
        fun setup() {
                diceClient = DiceClient(System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080")
                mockDatadog = MockDatadogClient()
                testFramework = DiceKnowledgeTestFramework(diceClient, mockDatadog)
        }

        // =========================================================================
        // DATABASE SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - database pool exhaustion`() {
                val contextId = "test-db-pool-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-db-pool",
                                                        name =
                                                                "Database Connection Pool Exhaustion",
                                                        description = "HikariCP pool saturated",
                                                        symptoms =
                                                                listOf(
                                                                        "High latency on API endpoints",
                                                                        "TimeoutError: Connection pool exhausted",
                                                                        "jvm.db.pool.active at maximum"
                                                                ),
                                                        rootCause =
                                                                "Connection pool exhausted due to slow queries or connection leaks",
                                                        resolution =
                                                                "Check slow queries, increase pool size, fix connection leaks",
                                                        affectedServices = listOf("api-service")
                                                )
                                        ),
                                dependencies =
                                        listOf(
                                                ServiceDependency(
                                                        "api-service",
                                                        "postgres",
                                                        "database",
                                                        true,
                                                        "HikariCP max=100"
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("db-pool", createDbPoolScenario())
                mockDatadog.setActiveScenario("db-pool")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "API P95 Latency Alert",
                                        service = "api-service",
                                        message = "P95 latency exceeded 500ms. Current: 2450ms",
                                        metricsToQuery =
                                                listOf("jvm.db.pool.active{service:api-service}"),
                                        logQueries = listOf("service:api-service status:error")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "connection pool",
                                                        "exhausted",
                                                        "database",
                                                        "timeout"
                                                ),
                                        component = "database"
                                )
                        )

                println("DB Pool Test: ${verification.summary()}")
                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - database deadlock`() {
                val contextId = "test-deadlock-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-deadlock",
                                                        name = "Database Deadlock",
                                                        description =
                                                                "Concurrent transactions causing deadlocks",
                                                        symptoms =
                                                                listOf(
                                                                        "PSQLException: deadlock detected",
                                                                        "Transaction rollbacks increasing",
                                                                        "Retry exhaustion on writes"
                                                                ),
                                                        rootCause =
                                                                "Concurrent updates accessing rows in different order",
                                                        resolution =
                                                                "Fix transaction ordering, use SELECT FOR UPDATE",
                                                        affectedServices =
                                                                listOf("inventory-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("deadlock", createDeadlockScenario())
                mockDatadog.setActiveScenario("deadlock")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Inventory Service Error Rate",
                                        service = "inventory-service",
                                        message =
                                                "Error rate exceeded threshold with transaction failures",
                                        logQueries = listOf("service:inventory-service deadlock")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "deadlock",
                                                        "transaction",
                                                        "concurrent",
                                                        "lock"
                                                ),
                                        component = "database"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - disk io saturation`() {
                val contextId = "test-disk-io-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-disk-io",
                                                        name = "Disk I/O Saturation",
                                                        description =
                                                                "Heavy I/O operations saturating disk",
                                                        symptoms =
                                                                listOf(
                                                                        "system.io.util > 90%",
                                                                        "High I/O await times",
                                                                        "Slow database queries"
                                                                ),
                                                        rootCause =
                                                                "Backup or heavy batch job consuming I/O bandwidth",
                                                        resolution =
                                                                "Reschedule backup to off-peak, use I/O throttling",
                                                        affectedServices =
                                                                listOf("order-service", "postgres")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("disk-io", createDiskIoScenario())
                mockDatadog.setActiveScenario("disk-io")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Database Query Latency",
                                        service = "order-service",
                                        message = "Database queries slow, potential I/O issue",
                                        metricsToQuery = listOf("system.io.util{host:db-primary}")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("disk", "I/O", "backup", "saturation"),
                                        component = "storage"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // KUBERNETES SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - kubernetes oom killed`() {
                val contextId = "test-k8s-oom-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-oom",
                                                        name = "Kubernetes OOMKilled",
                                                        description =
                                                                "Pod terminated due to memory limit exceeded",
                                                        symptoms =
                                                                listOf(
                                                                        "Container restarts increasing",
                                                                        "kubernetes.memory.usage at limit",
                                                                        "OutOfMemoryError in logs",
                                                                        "Exit code 137"
                                                                ),
                                                        rootCause =
                                                                "Memory leak or insufficient memory limits",
                                                        resolution =
                                                                "Fix memory leak, increase limits, add GC tuning",
                                                        affectedServices = listOf("order-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("k8s-oom", createK8sOomScenario())
                mockDatadog.setActiveScenario("k8s-oom")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Order Service Memory Alert",
                                        service = "order-service",
                                        message = "Memory usage critical, containers restarting",
                                        metricsToQuery =
                                                listOf(
                                                        "kubernetes.memory.usage{deployment:order-service}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("OOM", "memory", "killed", "limit"),
                                        component = "kubernetes"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - kubernetes node pressure`() {
                val contextId = "test-node-pressure-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-node-pressure",
                                                        name = "Kubernetes Node Memory Pressure",
                                                        description =
                                                                "Node under memory pressure, evicting pods",
                                                        symptoms =
                                                                listOf(
                                                                        "Pod evictions",
                                                                        "FailedScheduling events",
                                                                        "Node status MemoryPressure",
                                                                        "Multiple services affected"
                                                                ),
                                                        rootCause =
                                                                "Node memory exhausted, triggering evictions",
                                                        resolution =
                                                                "Add nodes, reduce pod memory, enable autoscaler",
                                                        affectedServices = listOf("multiple")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("node-pressure", createNodePressureScenario())
                mockDatadog.setActiveScenario("node-pressure")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Multiple Service Errors",
                                        message =
                                                "Multiple services experiencing errors simultaneously",
                                        logQueries = listOf("kubernetes.event.reason:Evicted")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("node", "memory", "pressure", "evict"),
                                        component = "kubernetes"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - cpu throttling`() {
                val contextId = "test-cpu-throttle-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-cpu-throttle",
                                                        name = "Kubernetes CPU Throttling",
                                                        description =
                                                                "Container CPU limits causing throttling",
                                                        symptoms =
                                                                listOf(
                                                                        "Latency spikes during peak traffic",
                                                                        "kubernetes.cpu.cfs.throttled.seconds high",
                                                                        "CPU at limit but node has capacity"
                                                                ),
                                                        rootCause =
                                                                "CPU limits too restrictive for workload",
                                                        resolution =
                                                                "Increase CPU limits, enable VPA",
                                                        affectedServices = listOf("search-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("cpu-throttle", createCpuThrottleScenario())
                mockDatadog.setActiveScenario("cpu-throttle")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Search Service Latency",
                                        service = "search-service",
                                        message = "Latency spikes during traffic peak",
                                        metricsToQuery =
                                                listOf(
                                                        "kubernetes.cpu.cfs.throttled.seconds{deployment:search-service}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("CPU", "throttl", "limit"),
                                        component = "kubernetes"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // SERVICE MESH / NETWORKING SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - downstream service failure`() {
                val contextId = "test-downstream-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-downstream",
                                                        name = "Downstream Service Failure",
                                                        description =
                                                                "Downstream dependency failure causing cascade",
                                                        symptoms =
                                                                listOf(
                                                                        "Timeout errors to downstream service",
                                                                        "Circuit breaker OPEN",
                                                                        "503 errors from gateway"
                                                                ),
                                                        rootCause =
                                                                "Downstream service failure cascading upstream",
                                                        resolution =
                                                                "Fix downstream service, circuit breaker limits blast radius",
                                                        affectedServices =
                                                                listOf(
                                                                        "checkout-service",
                                                                        "payment-service"
                                                                )
                                                )
                                        ),
                                dependencies =
                                        listOf(
                                                ServiceDependency(
                                                        "checkout-service",
                                                        "payment-service",
                                                        "http",
                                                        true
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("downstream", createDownstreamScenario())
                mockDatadog.setActiveScenario("downstream")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Checkout Service Error Rate",
                                        service = "checkout-service",
                                        message = "Error rate exceeded 5%",
                                        logQueries = listOf("service:checkout-service circuit")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "payment-service",
                                                        "downstream",
                                                        "circuit breaker"
                                                ),
                                        component = "payment-service"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - dns resolution failure`() {
                val contextId = "test-dns-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-dns",
                                                        name = "DNS Resolution Failure",
                                                        description =
                                                                "CoreDNS or NodeLocal DNS issues",
                                                        symptoms =
                                                                listOf(
                                                                        "UnknownHostException errors",
                                                                        "DNS lookup timeouts",
                                                                        "Service discovery failures"
                                                                ),
                                                        rootCause =
                                                                "DNS infrastructure overwhelmed or crashed",
                                                        resolution =
                                                                "Scale CoreDNS, check NodeLocal DNS pods",
                                                        affectedServices = listOf("all services")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("dns", createDnsScenario())
                mockDatadog.setActiveScenario("dns")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Service Connectivity Issues",
                                        message = "Multiple services unable to resolve hostnames",
                                        logQueries =
                                                listOf(
                                                        "UnknownHostException OR 'Name does not resolve'"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("DNS", "resolution", "CoreDNS"),
                                        component = "dns"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - network partition`() {
                val contextId = "test-partition-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-partition",
                                                        name = "Network Partition",
                                                        description =
                                                                "Network split between availability zones",
                                                        symptoms =
                                                                listOf(
                                                                        "Partial service failures",
                                                                        "Cross-AZ communication failures",
                                                                        "Database replication lag/disconnect"
                                                                ),
                                                        rootCause = "Network partition between AZs",
                                                        resolution =
                                                                "Check network connectivity, failover to healthy AZ",
                                                        affectedServices =
                                                                listOf("cross-AZ services")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("partition", createNetworkPartitionScenario())
                mockDatadog.setActiveScenario("partition")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Cross-AZ Connectivity",
                                        message = "Services in AZ-1 cannot reach AZ-2",
                                        logQueries =
                                                listOf("'connection refused' OR 'no route to host'")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "network",
                                                        "partition",
                                                        "AZ",
                                                        "connectivity"
                                                ),
                                        component = "network"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - envoy sidecar failure`() {
                val contextId = "test-envoy-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-envoy",
                                                        name = "Envoy Sidecar Failure",
                                                        description = "Service mesh proxy crashing",
                                                        symptoms =
                                                                listOf(
                                                                        "istio-proxy container restarts",
                                                                        "Upstream connection failures",
                                                                        "503 from mesh"
                                                                ),
                                                        rootCause =
                                                                "Envoy proxy OOM or configuration issue",
                                                        resolution =
                                                                "Increase sidecar memory, check Istio config",
                                                        affectedServices = listOf("mesh services")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("envoy", createEnvoyScenario())
                mockDatadog.setActiveScenario("envoy")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Service Mesh Errors",
                                        message = "High 503 rate from service mesh",
                                        logQueries =
                                                listOf(
                                                        "istio-proxy OOM OR 'upstream connect error'"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("envoy", "sidecar", "istio", "proxy"),
                                        component = "service-mesh"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // CACHE SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - redis cache failure`() {
                val contextId = "test-redis-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-redis",
                                                        name = "Redis Cache Failure",
                                                        description =
                                                                "Cache unavailable, fallback to database",
                                                        symptoms =
                                                                listOf(
                                                                        "Cache miss rate 100%",
                                                                        "Redis connection errors",
                                                                        "Database load spike"
                                                                ),
                                                        rootCause =
                                                                "Redis server down or unreachable",
                                                        resolution =
                                                                "Restart Redis, check Sentinel/Cluster, add fallback",
                                                        affectedServices = listOf("product-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("redis", createRedisScenario())
                mockDatadog.setActiveScenario("redis")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Product Service Latency",
                                        service = "product-service",
                                        message = "Latency increased, cache miss rate high",
                                        metricsToQuery =
                                                listOf(
                                                        "app.cache.miss_rate{service:product-service}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("Redis", "cache", "miss", "connection"),
                                        component = "cache"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // MESSAGING SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - kafka consumer lag`() {
                val contextId = "test-kafka-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-kafka-lag",
                                                        name = "Kafka Consumer Lag",
                                                        description =
                                                                "Consumer falling behind, processing delays",
                                                        symptoms =
                                                                listOf(
                                                                        "Consumer lag increasing",
                                                                        "Data freshness SLA breach",
                                                                        "Slow message processing"
                                                                ),
                                                        rootCause =
                                                                "Consumer processing too slow (often N+1 queries)",
                                                        resolution =
                                                                "Optimize processing, batch queries, scale consumers",
                                                        affectedServices = listOf("event-processor")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("kafka", createKafkaLagScenario())
                mockDatadog.setActiveScenario("kafka")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Kafka Consumer Lag Alert",
                                        service = "event-processor",
                                        message = "Consumer lag exceeded 100k messages",
                                        metricsToQuery =
                                                listOf(
                                                        "kafka.consumer.lag{consumer_group:event-processor}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("Kafka", "consumer", "lag", "processing"),
                                        component = "messaging"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // JVM / APPLICATION SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - gc storm`() {
                val contextId = "test-gc-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-gc",
                                                        name = "Garbage Collection Storm",
                                                        description =
                                                                "Excessive GC causing application pauses",
                                                        symptoms =
                                                                listOf(
                                                                        "Long GC pauses (seconds)",
                                                                        "GC overhead > 80%",
                                                                        "High latency spikes"
                                                                ),
                                                        rootCause =
                                                                "Memory pressure causing constant GC",
                                                        resolution =
                                                                "Fix memory leak, increase heap, tune GC",
                                                        affectedServices = listOf("report-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("gc", createGcScenario())
                mockDatadog.setActiveScenario("gc")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Report Service Latency",
                                        service = "report-service",
                                        message = "Extreme latency spikes, possible GC issue",
                                        metricsToQuery =
                                                listOf("jvm.gc.pause_time{service:report-service}")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "GC",
                                                        "garbage collection",
                                                        "heap",
                                                        "memory"
                                                ),
                                        component = "jvm"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - thread pool exhaustion`() {
                val contextId = "test-threads-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-threads",
                                                        name = "Thread Pool Exhaustion",
                                                        description =
                                                                "Thread pool saturated by blocking calls",
                                                        symptoms =
                                                                listOf(
                                                                        "Thread pool at max",
                                                                        "Request rejections",
                                                                        "Threads blocked on I/O"
                                                                ),
                                                        rootCause =
                                                                "Slow downstream causing thread blocking",
                                                        resolution =
                                                                "Add timeouts, use async I/O, implement bulkhead",
                                                        affectedServices = listOf("api-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("threads", createThreadPoolScenario())
                mockDatadog.setActiveScenario("threads")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "API Service 503 Errors",
                                        service = "api-service",
                                        message = "503 errors due to request rejections",
                                        metricsToQuery =
                                                listOf(
                                                        "jvm.thread.pool.active{service:api-service}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("thread", "pool", "exhausted", "blocked"),
                                        component = "application"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // SECURITY / AUTH SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - ssl certificate expiry`() {
                val contextId = "test-ssl-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-ssl",
                                                        name = "SSL Certificate Expiry",
                                                        description =
                                                                "Expired certificate causing TLS failures",
                                                        symptoms =
                                                                listOf(
                                                                        "SSLHandshakeException",
                                                                        "Certificate has expired",
                                                                        "All clients failing to connect"
                                                                ),
                                                        rootCause = "TLS certificate expired",
                                                        resolution =
                                                                "Renew certificate immediately",
                                                        affectedServices = listOf("auth-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("ssl", createSslScenario())
                mockDatadog.setActiveScenario("ssl")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "TLS Handshake Errors",
                                        service = "auth-service",
                                        message = "TLS handshake failures spiking",
                                        logQueries =
                                                listOf(
                                                        "SSLHandshakeException OR 'certificate expired'"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("SSL", "TLS", "certificate", "expired"),
                                        component = "security"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - secret rotation failure`() {
                val contextId = "test-secret-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-secret",
                                                        name = "Secret Rotation Failure",
                                                        description =
                                                                "Credential rotation not propagated to pods",
                                                        symptoms =
                                                                listOf(
                                                                        "Authentication failures to database",
                                                                        "Password authentication failed",
                                                                        "Pods using stale credentials"
                                                                ),
                                                        rootCause =
                                                                "Secret rotated but pods not restarted",
                                                        resolution =
                                                                "Restart pods, implement secret sync",
                                                        affectedServices = listOf("user-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("secret", createSecretRotationScenario())
                mockDatadog.setActiveScenario("secret")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "User Service Database Errors",
                                        service = "user-service",
                                        message = "Database authentication failures",
                                        logQueries = listOf("'password authentication failed'")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "secret",
                                                        "password",
                                                        "credential",
                                                        "rotation"
                                                ),
                                        component = "security"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // CONFIGURATION SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - config drift`() {
                val contextId = "test-config-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-config",
                                                        name = "Configuration Drift",
                                                        description =
                                                                "ConfigMap change not propagated to all pods",
                                                        symptoms =
                                                                listOf(
                                                                        "Intermittent failures",
                                                                        "Some pods succeed, some fail",
                                                                        "Different config versions across pods"
                                                                ),
                                                        rootCause =
                                                                "Config change not propagated, pods not restarted",
                                                        resolution =
                                                                "Rolling restart, implement config reloader",
                                                        affectedServices = listOf("order-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("config", createConfigDriftScenario())
                mockDatadog.setActiveScenario("config")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Order Service Intermittent Errors",
                                        service = "order-service",
                                        message = "Intermittent failures, some pods affected",
                                        logQueries = listOf("service:order-service status:error")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("config", "drift", "propagat"),
                                        component = "configuration"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // EXTERNAL / THIRD-PARTY SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - third party api degradation`() {
                val contextId = "test-3rd-party-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-3rdparty",
                                                        name = "Third-Party API Degradation",
                                                        description =
                                                                "External API experiencing issues",
                                                        symptoms =
                                                                listOf(
                                                                        "Timeouts to external API",
                                                                        "Elevated error rates from provider",
                                                                        "Circuit breaker opens"
                                                                ),
                                                        rootCause =
                                                                "Third-party service degradation (outside our control)",
                                                        resolution =
                                                                "Enable fallbacks, monitor provider status",
                                                        affectedServices =
                                                                listOf("checkout-service")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("3rdparty", createThirdPartyScenario())
                mockDatadog.setActiveScenario("3rdparty")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Checkout Service Errors",
                                        service = "checkout-service",
                                        message = "Payment processing failures",
                                        logQueries = listOf("Stripe OR 'external API'")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "Stripe",
                                                        "third-party",
                                                        "external",
                                                        "provider"
                                                ),
                                        component = "external"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - rate limiting`() {
                val contextId = "test-ratelimit-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-ratelimit",
                                                        name = "Rate Limiting",
                                                        description =
                                                                "Rate limits hit due to traffic spike or abuse",
                                                        symptoms =
                                                                listOf(
                                                                        "429 Too Many Requests",
                                                                        "Rate limit bucket exhausted",
                                                                        "Legitimate traffic affected"
                                                                ),
                                                        rootCause =
                                                                "Shared rate limit consumed by abusive client",
                                                        resolution =
                                                                "Implement per-client limits, block abusers",
                                                        affectedServices = listOf("api-gateway")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("ratelimit", createRateLimitScenario())
                mockDatadog.setActiveScenario("ratelimit")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "API Gateway 429 Errors",
                                        service = "api-gateway",
                                        message = "High 429 rate, legitimate users affected",
                                        metricsToQuery = listOf("http.responses{status_code:429}")
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("rate limit", "429", "throttl"),
                                        component = "traffic"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // SEARCH / ELASTICSEARCH SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - elasticsearch cluster issue`() {
                val contextId = "test-es-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-es",
                                                        name = "Elasticsearch Cluster Issue",
                                                        description =
                                                                "ES cluster degraded due to node failure",
                                                        symptoms =
                                                                listOf(
                                                                        "Cluster status RED",
                                                                        "Unassigned shards",
                                                                        "Search timeouts/partial results"
                                                                ),
                                                        rootCause =
                                                                "Data node failure causing shard unavailability",
                                                        resolution =
                                                                "Add/restart nodes, rebalance shards",
                                                        affectedServices = listOf("search-api")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("es", createElasticsearchScenario())
                mockDatadog.setActiveScenario("es")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Search Service Errors",
                                        service = "search-api",
                                        message = "Search failures and timeouts",
                                        metricsToQuery =
                                                listOf(
                                                        "elasticsearch.cluster.health.status{cluster:search-cluster}"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf("Elasticsearch", "cluster", "shard", "node"),
                                        component = "search"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // INVESTIGATION WORKFLOW SCENARIOS
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - investigation workflow latency`() {
                val contextId = "test-inv-latency-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                architecture =
                                        SystemArchitecture(
                                                name = "E-Commerce",
                                                description = "Microservices platform",
                                                services =
                                                        listOf(
                                                                ServiceInfo(
                                                                        "api-service",
                                                                        "API Gateway",
                                                                        "gateway",
                                                                        listOf("inventory-service"),
                                                                        true
                                                                ),
                                                                ServiceInfo(
                                                                        "inventory-service",
                                                                        "Inventory",
                                                                        "service",
                                                                        listOf("postgres"),
                                                                        true
                                                                )
                                                        )
                                        ),
                                dependencies =
                                        listOf(
                                                ServiceDependency(
                                                        "api-service",
                                                        "inventory-service",
                                                        "http",
                                                        true
                                                ),
                                                ServiceDependency(
                                                        "inventory-service",
                                                        "postgres",
                                                        "database",
                                                        true
                                                )
                                        ),
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-cascade",
                                                        name = "Database Blocking Queries",
                                                        description =
                                                                "Autovacuum or long queries blocking normal operations",
                                                        symptoms =
                                                                listOf(
                                                                        "High query latency",
                                                                        "Lock wait timeouts"
                                                                ),
                                                        rootCause =
                                                                "Database maintenance or blocking operation",
                                                        resolution =
                                                                "Check autovacuum, long-running queries",
                                                        affectedServices =
                                                                listOf(
                                                                        "inventory-service",
                                                                        "api-service"
                                                                )
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("inv-latency", createInvestigationLatencyScenario())
                mockDatadog.setActiveScenario("inv-latency")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "API P95 Latency Alert",
                                        service = "api-service",
                                        message = "P95 latency 2450ms (threshold 500ms)",
                                        metricsToQuery =
                                                listOf(
                                                        "trace.api-service.request.duration{env:prod}",
                                                        "trace.inventory-service.request.duration{env:prod}",
                                                        "postgresql.queries.time{db:inventory-db}"
                                                ),
                                        logQueries =
                                                listOf(
                                                        "service:api-service",
                                                        "service:inventory-service",
                                                        "autovacuum"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "autovacuum",
                                                        "postgres",
                                                        "database",
                                                        "blocking"
                                                ),
                                        component = "database"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - investigation workflow errors`() {
                val contextId = "test-inv-errors-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                dependencies =
                                        listOf(
                                                ServiceDependency(
                                                        "order-service",
                                                        "auth-service",
                                                        "http",
                                                        true
                                                )
                                        ),
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-auth-config",
                                                        name = "Auth Configuration Issue",
                                                        description =
                                                                "OAuth config change invalidates tokens",
                                                        symptoms =
                                                                listOf(
                                                                        "401 errors spike",
                                                                        "issuer_mismatch",
                                                                        "auth-service shows no errors"
                                                                ),
                                                        rootCause =
                                                                "Config change removed valid OAuth issuer",
                                                        resolution =
                                                                "Revert config, gradual migration",
                                                        affectedServices =
                                                                listOf("all authenticated services")
                                                )
                                        ),
                                pastIncidents =
                                        listOf(
                                                PastIncident(
                                                        id = "INC-001",
                                                        title = "Auth Config Caused 401s",
                                                        date = "2024-10-15",
                                                        durationMinutes = 20,
                                                        severity = "HIGH",
                                                        symptoms =
                                                                listOf(
                                                                        "401 everywhere",
                                                                        "auth-service healthy"
                                                                ),
                                                        rootCause =
                                                                "Removed old issuer from allowed list",
                                                        resolution = "Added back old issuer",
                                                        lessonsLearned =
                                                                listOf(
                                                                        "When auth-service healthy but 401s spike, check config"
                                                                )
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario("inv-errors", createInvestigationErrorsScenario())
                mockDatadog.setActiveScenario("inv-errors")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Order Service Error Rate Alert",
                                        service = "order-service",
                                        message = "Error rate 23% (401 Unauthorized)",
                                        logQueries =
                                                listOf(
                                                        "service:order-service 401",
                                                        "service:auth-service issuer"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("config", "issuer", "OAuth", "auth"),
                                        component = "auth-service"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `scenario - investigation workflow intermittent`() {
                val contextId = "test-inv-intermittent-${System.currentTimeMillis()}"

                testFramework.loadPriorKnowledge(
                        contextId,
                        PriorKnowledge(
                                failurePatterns =
                                        listOf(
                                                FailurePattern(
                                                        id = "fp-node-dns",
                                                        name = "Node-Specific DNS Failure",
                                                        description =
                                                                "NodeLocal DNSCache failure on specific node",
                                                        symptoms =
                                                                listOf(
                                                                        "Intermittent failures (not all requests)",
                                                                        "Errors correlate to specific pods",
                                                                        "DNS resolution errors"
                                                                ),
                                                        rootCause =
                                                                "NodeLocal DNSCache crashed on one node",
                                                        resolution =
                                                                "Restart DNS cache, check node resources",
                                                        affectedServices =
                                                                listOf("pods on affected node")
                                                )
                                        )
                        )
                )

                mockDatadog.loadScenario(
                        "inv-intermittent",
                        createInvestigationIntermittentScenario()
                )
                mockDatadog.setActiveScenario("inv-intermittent")

                val result =
                        testFramework.simulateAlert(
                                contextId,
                                TestAlert(
                                        name = "Checkout Service Intermittent 503s",
                                        service = "checkout-service",
                                        message = "8% error rate, intermittent 503s",
                                        metricsToQuery = listOf("dns.lookup.errors{*}"),
                                        logQueries =
                                                listOf(
                                                        "'Name does not resolve' OR UnknownHostException"
                                                )
                                )
                        )

                val verification =
                        testFramework.verifyConclusion(
                                result,
                                ExpectedRootCause(
                                        keywords = listOf("DNS", "node", "NodeLocal"),
                                        component = "dns"
                                )
                        )

                if (!verification.passed) {
                        logger.error("Verification failed for test: ${this::class.simpleName}")
                        logger.error(verification.summary())
                        logger.error("Expected keywords: ${verification.expectedKeywords}")
                        logger.error("Keywords found: ${verification.keywordsFound}")
                        logger.error("Keywords missing: ${verification.keywordsMissing}")
                        logger.error(
                                "Keyword coverage: ${(verification.keywordCoverage * 100).toInt()}%"
                        )
                        logger.error("Component identified: ${verification.componentIdentified}")
                        logger.error("Actual root cause analysis:")
                        logger.error(verification.actualRootCause)
                }
                assertTrue(
                        verification.passed,
                        "Verification failed. ${verification.summary()}\n" +
                                "Expected keywords: ${verification.expectedKeywords}\n" +
                                "Found: ${verification.keywordsFound}\n" +
                                "Missing: ${verification.keywordsMissing}\n" +
                                "Coverage: ${(verification.keywordCoverage * 100).toInt()}%\n" +
                                "Actual analysis: ${verification.actualRootCause.take(500)}"
                )
        }

        // =========================================================================
        // MOCK SCENARIO HELPERS
        // =========================================================================

        private fun createDbPoolScenario() =
                scenario("db-pool") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-service",
                                                "api-1",
                                                "error",
                                                "TimeoutError: Connection pool exhausted after 5000ms",
                                                mapOf("error.type" to "TimeoutError")
                                        )
                                )
                        )
                }

        private fun createDeadlockScenario() =
                scenario("deadlock") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "inventory-service",
                                                "inv-1",
                                                "error",
                                                "PSQLException: ERROR: deadlock detected",
                                                mapOf("error.type" to "PSQLException")
                                        )
                                )
                        )
                }

        private fun createDiskIoScenario() =
                scenario("disk-io") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "postgresql",
                                                "db-1",
                                                "warn",
                                                "I/O wait high: autovacuum and backup running concurrently",
                                                mapOf("io.util" to 99)
                                        )
                                )
                        )
                }

        private fun createK8sOomScenario() =
                scenario("k8s-oom") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "kubernetes",
                                                "node-1",
                                                "error",
                                                "Container order-service OOMKilled",
                                                mapOf("kubernetes.event.reason" to "OOMKilled")
                                        )
                                )
                        )
                }

        private fun createNodePressureScenario() =
                scenario("node-pressure") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "kubernetes",
                                                "node-3",
                                                "warn",
                                                "Evicting pod due to node memory pressure",
                                                mapOf("kubernetes.event.reason" to "Evicted")
                                        )
                                )
                        )
                }

        private fun createCpuThrottleScenario() =
                scenario("cpu-throttle") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "search-service",
                                                "search-1",
                                                "warn",
                                                "CPU throttling detected: 25 seconds throttled in last minute",
                                                mapOf("cpu.throttled_seconds" to 25)
                                        )
                                )
                        )
                }

        private fun createDownstreamScenario() =
                scenario("downstream") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "checkout-service",
                                                "checkout-1",
                                                "error",
                                                "Failed to connect to payment-service: Connection timeout",
                                                mapOf("downstream.service" to "payment-service")
                                        ),
                                        LogEntry(
                                                "2",
                                                now,
                                                "checkout-service",
                                                "checkout-1",
                                                "warn",
                                                "CircuitBreaker 'payment-service' state changed: CLOSED -> OPEN",
                                                mapOf("circuit.name" to "payment-service")
                                        )
                                )
                        )
                }

        private fun createDnsScenario() =
                scenario("dns") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-gateway",
                                                "gw-1",
                                                "error",
                                                "UnknownHostException: order-service.production.svc.cluster.local",
                                                mapOf("error.type" to "UnknownHostException")
                                        )
                                )
                        )
                }

        private fun createNetworkPartitionScenario() =
                scenario("partition") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "network-monitor",
                                                "mon-1",
                                                "error",
                                                "Network partition detected between az-1 and az-2",
                                                mapOf(
                                                        "partition.source" to "az-1",
                                                        "partition.dest" to "az-2"
                                                )
                                        )
                                )
                        )
                }

        private fun createEnvoyScenario() =
                scenario("envoy") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "istio-proxy",
                                                "payment-1",
                                                "error",
                                                "Envoy proxy terminated: OOMKilled",
                                                mapOf(
                                                        "container" to "istio-proxy",
                                                        "termination.reason" to "OOMKilled"
                                                )
                                        )
                                )
                        )
                }

        private fun createRedisScenario() =
                scenario("redis") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "product-service",
                                                "prod-1",
                                                "error",
                                                "RedisConnectionException: Unable to connect to redis-primary:6379",
                                                mapOf("error.type" to "RedisConnectionException")
                                        )
                                )
                        )
                }

        private fun createKafkaLagScenario() =
                scenario("kafka") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "event-processor",
                                                "proc-1",
                                                "warn",
                                                "Consumer lag critical: 500,000 messages behind",
                                                mapOf("kafka.lag" to 500000)
                                        )
                                )
                        )
                }

        private fun createGcScenario() =
                scenario("gc") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "report-service",
                                                "report-1",
                                                "error",
                                                "GC overhead limit exceeded - spent 85% of time in GC",
                                                mapOf("gc.overhead_percent" to 85)
                                        )
                                )
                        )
                }

        private fun createThreadPoolScenario() =
                scenario("threads") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-service",
                                                "api-1",
                                                "error",
                                                "Thread pool exhausted: 200/200 active, rejecting requests",
                                                mapOf("pool.active" to 200, "pool.max" to 200)
                                        )
                                )
                        )
                }

        private fun createSslScenario() =
                scenario("ssl") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-gateway",
                                                "gw-1",
                                                "error",
                                                "SSLHandshakeException: PKIX path validation failed: certificate has expired",
                                                mapOf("error.type" to "SSLHandshakeException")
                                        )
                                )
                        )
                }

        private fun createSecretRotationScenario() =
                scenario("secret") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "user-service",
                                                "user-1",
                                                "error",
                                                "PSQLException: FATAL: password authentication failed for user 'user_service_app'",
                                                mapOf("error.type" to "PSQLException")
                                        )
                                )
                        )
                }

        private fun createConfigDriftScenario() =
                scenario("config") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "order-service",
                                                "order-pod-1",
                                                "error",
                                                "Connection refused to orders-db-old.prod.svc",
                                                mapOf("db.host" to "orders-db-old.prod.svc")
                                        ),
                                        LogEntry(
                                                "2",
                                                now,
                                                "order-service",
                                                "order-pod-2",
                                                "info",
                                                "Order created successfully",
                                                mapOf("db.host" to "orders-db-new.prod.svc")
                                        )
                                )
                        )
                }

        private fun createThirdPartyScenario() =
                scenario("3rdparty") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "checkout-service",
                                                "checkout-1",
                                                "error",
                                                "HTTP 503 from Stripe: Service Unavailable",
                                                mapOf(
                                                        "provider" to "stripe",
                                                        "http.status_code" to 503
                                                )
                                        )
                                )
                        )
                }

        private fun createRateLimitScenario() =
                scenario("ratelimit") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-gateway",
                                                "gw-1",
                                                "warn",
                                                "Rate limit bucket exhausted, rejecting requests",
                                                mapOf("rate_limit.rejection_count" to 500)
                                        )
                                )
                        )
                }

        private fun createElasticsearchScenario() =
                scenario("es") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "elasticsearch",
                                                "es-1",
                                                "error",
                                                "Cluster health changed: GREEN -> RED, unassigned shards: 30",
                                                mapOf(
                                                        "cluster.health" to "RED",
                                                        "unassigned_shards" to 30
                                                )
                                        )
                                )
                        )
                }

        private fun createInvestigationLatencyScenario() =
                scenario("inv-latency") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "api-service",
                                                "api-1",
                                                "error",
                                                "Timeout waiting for inventory-service",
                                                mapOf("downstream.service" to "inventory-service")
                                        ),
                                        LogEntry(
                                                "2",
                                                now,
                                                "inventory-service",
                                                "inv-1",
                                                "error",
                                                "Query timeout: SELECT * FROM inventory",
                                                mapOf("error.type" to "QueryTimeoutException")
                                        ),
                                        LogEntry(
                                                "3",
                                                now.minus(45, ChronoUnit.MINUTES),
                                                "postgresql",
                                                "db-1",
                                                "warn",
                                                "Autovacuum running on table 'inventory' - blocking queries",
                                                mapOf("db.operation" to "autovacuum")
                                        )
                                )
                        )
                }

        private fun createInvestigationErrorsScenario() =
                scenario("inv-errors") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "order-service",
                                                "order-1",
                                                "error",
                                                "Authentication failed: 401 Unauthorized",
                                                mapOf("http.status_code" to 401)
                                        ),
                                        LogEntry(
                                                "2",
                                                now,
                                                "auth-service",
                                                "auth-1",
                                                "warn",
                                                "Token validation failed: issuer mismatch. Expected: https://auth-v2.example.com",
                                                mapOf("validation.error" to "issuer_mismatch")
                                        )
                                )
                        )
                        events(
                                listOf(
                                        EventEntry(
                                                1,
                                                "Config Update: auth-service-config",
                                                "Removed old issuer from allowed_issuers",
                                                now.minus(5, ChronoUnit.MINUTES),
                                                source = "kubectl",
                                                tags = listOf("config", "auth-service")
                                        )
                                )
                        )
                }

        private fun createInvestigationIntermittentScenario() =
                scenario("inv-intermittent") {
                        val now = Instant.now()
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))
                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                "1",
                                                now,
                                                "checkout-service",
                                                "checkout-ghi44",
                                                "error",
                                                "DNS lookup failed: payment-service.production.svc.cluster.local: Name does not resolve",
                                                mapOf(
                                                        "kubernetes.pod.name" to "checkout-ghi44",
                                                        "kubernetes.node.name" to "worker-node-3"
                                                )
                                        ),
                                        LogEntry(
                                                "2",
                                                now,
                                                "node-local-dns",
                                                "worker-node-3",
                                                "error",
                                                "NodeLocal DNSCache crashed: OOM",
                                                mapOf(
                                                        "kubernetes.event.reason" to "OOMKilled",
                                                        "kubernetes.node.name" to "worker-node-3"
                                                )
                                        )
                                )
                        )
                }
}
