package com.example.rca.dice

import com.example.rca.datadog.dto.*
import com.example.rca.mock.MockDatadogClient
import com.example.rca.mock.scenario
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Integration tests for DICE-based RCA.
 *
 * These tests:
 * 1. Load PRIOR KNOWLEDGE into DICE (system architecture, past incidents, failure patterns)
 * 2. Simulate a NEW ALERT
 * 3. Verify the system reaches the CORRECT ROOT CAUSE conclusion
 */
class DiceRcaIntegrationTest {

        private lateinit var diceClient: DiceClient
        private lateinit var mockDatadog: MockDatadogClient
        private lateinit var testFramework: DiceKnowledgeTestFramework

        @BeforeEach
        fun setup() {
                // Use mock DICE client for testing (or real one if configured)
                diceClient = createTestDiceClient()
                mockDatadog = MockDatadogClient()
                testFramework = DiceKnowledgeTestFramework(diceClient, mockDatadog)
        }

        // =========================================================================
        // TEST 1: Database Pool Exhaustion
        // Prior knowledge: System architecture, past DB incidents
        // New alert: API latency spike
        // Expected conclusion: Database connection pool exhausted
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `should identify database pool exhaustion from latency alert`() {
                val contextId = "test-db-pool-${System.currentTimeMillis()}"

                runTestWithReporting(
                        testName = "should identify database pool exhaustion from latency alert",
                        contextId = contextId
                ) { reportBuilder ->
                        // Step 1: Load prior knowledge
                        val priorKnowledge = createDatabaseScenarioPriorKnowledge()
                        val loadStart = System.currentTimeMillis()
                        val loadResult = testFramework.loadPriorKnowledge(contextId, priorKnowledge)
                        val loadDuration = System.currentTimeMillis() - loadStart
                        println(
                                "Loaded ${loadResult.documentsLoaded} documents with ${loadResult.propositionsExtracted} propositions"
                        )

                        reportBuilder.withPriorKnowledge(
                                loadResult = loadResult,
                                dependenciesCount = priorKnowledge.dependencies.size,
                                failurePatternsCount = priorKnowledge.failurePatterns.size,
                                loadDurationMs = loadDuration
                        )

                        // Step 2: Setup mock Datadog data
                        mockDatadog.loadScenario(
                                "db-pool-exhaustion",
                                createDbPoolExhaustionScenario()
                        )
                        mockDatadog.setActiveScenario("db-pool-exhaustion")

                        // Step 3: Simulate alert
                        val alert =
                                TestAlert(
                                        name = "API P95 Latency Alert",
                                        service = "api-service",
                                        message =
                                                "P95 latency exceeded 500ms threshold. Current value: 2450ms",
                                        metricQuery =
                                                "p95:trace.api-service.request.duration{env:prod}",
                                        currentValue = 2450.0,
                                        threshold = 500.0,
                                        metricsToQuery =
                                                listOf(
                                                        "p95:trace.api-service.request.duration{env:prod}",
                                                        "avg:jvm.db.pool.active{service:api-service}",
                                                        "sum:jvm.db.pool.timeout{service:api-service}"
                                                ),
                                        logQueries =
                                                listOf(
                                                        "service:api-service status:error",
                                                        "service:api-service pool exhausted"
                                                ),
                                        traceQueries = listOf("api-service" to "prod")
                                )

                        val analysisStart = System.currentTimeMillis()
                        val result =
                                testFramework.simulateAlert(contextId, alert, "db-pool-exhaustion")
                        val analysisDuration = System.currentTimeMillis() - analysisStart

                        reportBuilder.withAlert(alert.toAlertInfo())
                        reportBuilder.withAnalysis(result, analysisDuration)

                        // Step 4: Verify conclusion
                        val expectedRootCause =
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "connection pool",
                                                        "database",
                                                        "exhausted",
                                                        "HikariPool",
                                                        "timeout"
                                                ),
                                        component = "database",
                                        causeType = "pool exhaustion"
                                )
                        val verification = testFramework.verifyConclusion(result, expectedRootCause)

                        reportBuilder.withVerification(verification, expectedRootCause)

                        println(verification.summary())
                        println("\nActual root cause analysis:\n${result.rootCauseAnalysis}")

                        assertTrue(verification.passed, "Should identify database pool exhaustion")
                }
        }

        // =========================================================================
        // TEST 2: Downstream Service Failure
        // Prior knowledge: Service dependencies, circuit breaker patterns
        // New alert: Error rate spike
        // Expected conclusion: Downstream service failure causing cascade
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `should identify downstream failure from error rate alert`() {
                val contextId = "test-downstream-${System.currentTimeMillis()}"

                runTestWithReporting(
                        testName = "should identify downstream failure from error rate alert",
                        contextId = contextId
                ) { reportBuilder ->
                        // Step 1: Load prior knowledge about service dependencies
                        val priorKnowledge = createDownstreamScenarioPriorKnowledge()
                        val loadStart = System.currentTimeMillis()
                        val loadResult = testFramework.loadPriorKnowledge(contextId, priorKnowledge)
                        val loadDuration = System.currentTimeMillis() - loadStart

                        reportBuilder.withPriorKnowledge(
                                loadResult = loadResult,
                                dependenciesCount = priorKnowledge.dependencies.size,
                                failurePatternsCount = priorKnowledge.failurePatterns.size,
                                loadDurationMs = loadDuration
                        )

                        // Step 2: Setup mock Datadog data
                        mockDatadog.loadScenario(
                                "downstream-failure",
                                createDownstreamFailureScenario()
                        )
                        mockDatadog.setActiveScenario("downstream-failure")

                        // Step 3: Simulate alert
                        val alert =
                                TestAlert(
                                        name = "Order Service Error Rate Alert",
                                        service = "order-service",
                                        message =
                                                "Error rate exceeded 5% threshold. Current value: 23%",
                                        metricQuery =
                                                "sum:trace.order-service.request.errors{env:prod}.as_rate()",
                                        currentValue = 23.0,
                                        threshold = 5.0,
                                        metricsToQuery =
                                                listOf(
                                                        "sum:trace.order-service.request.errors{env:prod}",
                                                        "sum:trace.payment-service.request.errors{env:prod}",
                                                        "avg:circuit_breaker.payment.state{service:order-service}"
                                                ),
                                        logQueries =
                                                listOf(
                                                        "service:order-service status:error",
                                                        "service:payment-service status:error"
                                                ),
                                        traceQueries =
                                                listOf(
                                                        "order-service" to "prod",
                                                        "payment-service" to "prod"
                                                )
                                )

                        val analysisStart = System.currentTimeMillis()
                        val result =
                                testFramework.simulateAlert(contextId, alert, "downstream-failure")
                        val analysisDuration = System.currentTimeMillis() - analysisStart

                        reportBuilder.withAlert(alert.toAlertInfo())
                        reportBuilder.withAnalysis(result, analysisDuration)

                        // Step 4: Verify conclusion
                        val expectedRootCause =
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "payment-service",
                                                        "downstream",
                                                        "circuit breaker",
                                                        "unavailable"
                                                ),
                                        component = "payment-service",
                                        causeType = "service failure"
                                )
                        val verification = testFramework.verifyConclusion(result, expectedRootCause)

                        reportBuilder.withVerification(verification, expectedRootCause)

                        println(verification.summary())
                        assertTrue(
                                verification.passed,
                                "Should identify downstream service failure"
                        )
                }
        }

        // =========================================================================
        // TEST 3: Config Change Causing Auth Failures
        // Prior knowledge: Auth service architecture, OAuth configuration
        // New alert: 401 error spike
        // Expected conclusion: Config change removed valid OAuth issuer
        // =========================================================================

        @Test
        @EnabledIfEnvironmentVariable(
                named = "DICE_SERVER_URL",
                matches = ".*",
                disabledReason = "Set DICE_SERVER_URL env var to run integration test"
        )
        fun `should identify config change from auth error alert`() {
                val contextId = "test-config-${System.currentTimeMillis()}"

                runTestWithReporting(
                        testName = "should identify config change from auth error alert",
                        contextId = contextId
                ) { reportBuilder ->
                        // Step 1: Load prior knowledge
                        val priorKnowledge = createAuthScenarioPriorKnowledge()
                        val loadStart = System.currentTimeMillis()
                        val loadResult = testFramework.loadPriorKnowledge(contextId, priorKnowledge)
                        val loadDuration = System.currentTimeMillis() - loadStart

                        reportBuilder.withPriorKnowledge(
                                loadResult = loadResult,
                                dependenciesCount = priorKnowledge.dependencies.size,
                                failurePatternsCount = priorKnowledge.failurePatterns.size,
                                loadDurationMs = loadDuration
                        )

                        // Step 2: Setup mock Datadog data
                        mockDatadog.loadScenario(
                                "auth-config-change",
                                createAuthConfigChangeScenario()
                        )
                        mockDatadog.setActiveScenario("auth-config-change")

                        // Step 3: Simulate alert
                        val alert =
                                TestAlert(
                                        name = "Order Service Error Rate Alert",
                                        service = "order-service",
                                        message =
                                                "Error rate 23%. High volume of 401 Unauthorized responses.",
                                        metricsToQuery =
                                                listOf(
                                                        "sum:http.responses{service:order-service,status_code:401}",
                                                        "sum:auth.token.validation.failures{service:auth-service}"
                                                ),
                                        logQueries =
                                                listOf(
                                                        "service:order-service status:error 401",
                                                        "service:auth-service issuer"
                                                ),
                                        traceQueries =
                                                listOf(
                                                        "order-service" to "prod",
                                                        "auth-service" to "prod"
                                                )
                                )

                        val analysisStart = System.currentTimeMillis()
                        val result =
                                testFramework.simulateAlert(contextId, alert, "auth-config-change")
                        val analysisDuration = System.currentTimeMillis() - analysisStart

                        reportBuilder.withAlert(alert.toAlertInfo())
                        reportBuilder.withAnalysis(result, analysisDuration)

                        // Step 4: Verify conclusion
                        val expectedRootCause =
                                ExpectedRootCause(
                                        keywords =
                                                listOf(
                                                        "config",
                                                        "issuer",
                                                        "OAuth",
                                                        "auth-service",
                                                        "allowed"
                                                ),
                                        component = "auth-service",
                                        causeType = "configuration change"
                                )
                        val verification = testFramework.verifyConclusion(result, expectedRootCause)

                        reportBuilder.withVerification(verification, expectedRootCause)

                        println(verification.summary())
                        assertTrue(
                                verification.passed,
                                "Should identify config change as root cause"
                        )
                }
        }

        // =========================================================================
        // Helper: Create prior knowledge for different scenarios
        // =========================================================================

        private fun createDatabaseScenarioPriorKnowledge() =
                PriorKnowledge(
                        architecture =
                                SystemArchitecture(
                                        name = "E-Commerce Platform",
                                        description = "Microservices-based e-commerce system",
                                        services =
                                                listOf(
                                                        ServiceInfo(
                                                                name = "api-service",
                                                                description = "Main API gateway",
                                                                type = "gateway",
                                                                dependencies =
                                                                        listOf(
                                                                                "inventory-service",
                                                                                "order-service",
                                                                                "postgres"
                                                                        ),
                                                                critical = true
                                                        ),
                                                        ServiceInfo(
                                                                name = "postgres",
                                                                description = "PostgreSQL database",
                                                                type = "database",
                                                                dependencies = emptyList(),
                                                                critical = true
                                                        )
                                                )
                                ),
                        dependencies =
                                listOf(
                                        ServiceDependency(
                                                from = "api-service",
                                                to = "postgres",
                                                type = "database",
                                                critical = true,
                                                notes =
                                                        "Uses HikariCP connection pool, max 100 connections"
                                        )
                                ),
                        failurePatterns =
                                listOf(
                                        FailurePattern(
                                                id = "fp-db-pool",
                                                name = "Database Connection Pool Exhaustion",
                                                description =
                                                        "Connection pool becomes saturated when queries are slow or connections aren't released",
                                                symptoms =
                                                        listOf(
                                                                "High latency on database-dependent endpoints",
                                                                "TimeoutError: Connection pool exhausted",
                                                                "HikariPool: Connection not available",
                                                                "Increasing pending connection requests"
                                                        ),
                                                rootCause =
                                                        "Database connection pool exhausted due to slow queries or connection leaks",
                                                resolution =
                                                        "1. Identify slow queries. 2. Check for connection leaks. 3. Increase pool size if needed.",
                                                affectedServices =
                                                        listOf("api-service", "order-service")
                                        )
                                ),
                        pastIncidents =
                                listOf(
                                        PastIncident(
                                                id = "INC-2024-001",
                                                title =
                                                        "API Latency Spike Due to DB Pool Exhaustion",
                                                date = "2024-12-15",
                                                durationMinutes = 45,
                                                severity = "HIGH",
                                                symptoms =
                                                        listOf(
                                                                "P95 latency spiked from 50ms to 5000ms",
                                                                "Connection pool at 100% utilization",
                                                                "Timeout errors in logs"
                                                        ),
                                                rootCause =
                                                        "A new query was holding connections too long after a deployment",
                                                resolution =
                                                        "Rolled back deployment and optimized the query",
                                                lessonsLearned =
                                                        listOf(
                                                                "Always test query performance in staging",
                                                                "Monitor connection pool metrics",
                                                                "Set query timeouts"
                                                        )
                                        )
                                ),
                        runbooks =
                                listOf(
                                        Runbook(
                                                id = "rb-high-latency",
                                                title = "High Latency Investigation",
                                                trigger = "P95 latency > 500ms",
                                                steps =
                                                        listOf(
                                                                "Check service CPU and memory",
                                                                "Check database connection pool utilization",
                                                                "Check for slow queries in database",
                                                                "Check for recent deployments",
                                                                "Check downstream service latency"
                                                        ),
                                                escalation = "If pool exhausted, engage DBA team"
                                        )
                                ),
                        slos =
                                listOf(
                                        ServiceLevelObjective(
                                                id = "slo-api-latency",
                                                name = "API Latency SLO",
                                                service = "api-service",
                                                metric = "p95:trace.api-service.request.duration",
                                                target = "< 500ms for 99% of requests",
                                                windowDays = 30
                                        )
                                )
                )

        private fun createDownstreamScenarioPriorKnowledge() =
                PriorKnowledge(
                        architecture =
                                SystemArchitecture(
                                        name = "E-Commerce Platform",
                                        description = "Microservices-based e-commerce system",
                                        services =
                                                listOf(
                                                        ServiceInfo(
                                                                "order-service",
                                                                "Handles order processing",
                                                                "service",
                                                                listOf(
                                                                        "payment-service",
                                                                        "inventory-service"
                                                                ),
                                                                true
                                                        ),
                                                        ServiceInfo(
                                                                "payment-service",
                                                                "Processes payments via Stripe",
                                                                "service",
                                                                listOf("stripe-api", "payment-db"),
                                                                true
                                                        )
                                                )
                                ),
                        dependencies =
                                listOf(
                                        ServiceDependency(
                                                "order-service",
                                                "payment-service",
                                                "http",
                                                true,
                                                "Circuit breaker configured with 50% failure threshold"
                                        )
                                ),
                        failurePatterns =
                                listOf(
                                        FailurePattern(
                                                id = "fp-downstream",
                                                name = "Downstream Service Failure Cascade",
                                                description =
                                                        "When a downstream service fails, it can cascade to upstream services",
                                                symptoms =
                                                        listOf(
                                                                "Error rate spike on upstream service",
                                                                "Timeouts calling downstream service",
                                                                "Circuit breaker opens",
                                                                "503 Service Unavailable responses"
                                                        ),
                                                rootCause =
                                                        "Downstream service failure causing cascade",
                                                resolution =
                                                        "1. Identify failing downstream service. 2. Check circuit breaker status. 3. Investigate root cause in downstream.",
                                                affectedServices =
                                                        listOf("order-service", "checkout-service")
                                        )
                                ),
                        pastIncidents =
                                listOf(
                                        PastIncident(
                                                id = "INC-2024-002",
                                                title =
                                                        "Order Failures Due to Payment Service Outage",
                                                date = "2024-11-20",
                                                durationMinutes = 30,
                                                severity = "CRITICAL",
                                                symptoms =
                                                        listOf(
                                                                "Order service errors spiked",
                                                                "Payment service returning 503",
                                                                "Circuit breaker opened"
                                                        ),
                                                rootCause =
                                                        "Payment service database was unreachable",
                                                resolution =
                                                        "Fixed network connectivity to payment-db",
                                                lessonsLearned =
                                                        listOf(
                                                                "Monitor downstream services alongside upstream",
                                                                "Circuit breaker limited the blast radius"
                                                        )
                                        )
                                )
                )

        private fun createAuthScenarioPriorKnowledge() =
                PriorKnowledge(
                        architecture =
                                SystemArchitecture(
                                        name = "E-Commerce Platform",
                                        description = "Microservices with centralized auth",
                                        services =
                                                listOf(
                                                        ServiceInfo(
                                                                "order-service",
                                                                "Order management",
                                                                "service",
                                                                listOf("auth-service"),
                                                                true
                                                        ),
                                                        ServiceInfo(
                                                                "auth-service",
                                                                "OAuth2 authentication",
                                                                "auth",
                                                                listOf("identity-db"),
                                                                true
                                                        )
                                                )
                                ),
                        dependencies =
                                listOf(
                                        ServiceDependency(
                                                "order-service",
                                                "auth-service",
                                                "http",
                                                true,
                                                "All requests validated against auth-service"
                                        )
                                ),
                        failurePatterns =
                                listOf(
                                        FailurePattern(
                                                id = "fp-auth-config",
                                                name = "Authentication Configuration Issue",
                                                description =
                                                        "Auth configuration changes can invalidate existing tokens",
                                                symptoms =
                                                        listOf(
                                                                "Spike in 401 Unauthorized responses",
                                                                "Token validation failures with 'issuer_mismatch'",
                                                                "Multiple services affected simultaneously",
                                                                "Auth service shows NO errors (correctly rejecting)"
                                                        ),
                                                rootCause =
                                                        "Configuration change in auth-service (e.g., allowed issuers)",
                                                resolution =
                                                        "Review recent config changes to auth-service",
                                                affectedServices =
                                                        listOf("All services using auth-service")
                                        )
                                ),
                        pastIncidents =
                                listOf(
                                        PastIncident(
                                                id = "INC-2024-003",
                                                title = "Mass Auth Failures After Config Update",
                                                date = "2024-10-15",
                                                durationMinutes = 20,
                                                severity = "HIGH",
                                                symptoms =
                                                        listOf(
                                                                "401 errors across all services",
                                                                "Users unable to authenticate",
                                                                "auth-service showing 100% success (correctly rejecting)"
                                                        ),
                                                rootCause =
                                                        "ConfigMap update removed old OAuth issuer from allowed list",
                                                resolution =
                                                        "Added old issuer back, implemented gradual migration",
                                                lessonsLearned =
                                                        listOf(
                                                                "Auth config changes need canary rollout",
                                                                "When auth-service has no errors but clients see 401s, check config"
                                                        )
                                        )
                                )
                )

        // =========================================================================
        // Helper: Create mock Datadog scenarios
        // =========================================================================

        private fun createDbPoolExhaustionScenario() =
                scenario("db-pool-exhaustion") {
                        val now = Instant.now()
                        description("Database connection pool exhausted")
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))

                        monitor(
                                MonitorResponse(
                                        id = 12345,
                                        name = "API P95 Latency",
                                        type = "metric alert",
                                        query =
                                                "p95:trace.api-service.request.duration{env:prod} > 500",
                                        tags = listOf("service:api-service", "env:prod")
                                )
                        )

                        baselineMetrics(
                                MetricResponse(
                                        series =
                                                listOf(
                                                        MetricSeries(
                                                                metric =
                                                                        "trace.api-service.request.duration",
                                                                pointlist =
                                                                        listOf(
                                                                                MetricPoint(
                                                                                        now.minus(
                                                                                                        60,
                                                                                                        ChronoUnit
                                                                                                                .MINUTES
                                                                                                )
                                                                                                .toEpochMilli(),
                                                                                        50.0
                                                                                )
                                                                        )
                                                        )
                                                )
                                )
                        )

                        incidentMetrics(
                                MetricResponse(
                                        series =
                                                listOf(
                                                        MetricSeries(
                                                                metric =
                                                                        "trace.api-service.request.duration",
                                                                pointlist =
                                                                        listOf(
                                                                                MetricPoint(
                                                                                        now.toEpochMilli(),
                                                                                        2450.0
                                                                                )
                                                                        )
                                                        )
                                                )
                                )
                        )

                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                id = "log-1",
                                                timestamp = now,
                                                service = "api-service",
                                                status = "error",
                                                message =
                                                        "TimeoutError: Connection pool exhausted after 5000ms",
                                                attributes = mapOf("error.type" to "TimeoutError")
                                        ),
                                        LogEntry(
                                                id = "log-2",
                                                timestamp = now,
                                                service = "api-service",
                                                status = "error",
                                                message =
                                                        "HikariPool-1: Connection not available, request timed out after 5000ms",
                                                attributes = mapOf("pool.name" to "HikariPool-1")
                                        )
                                )
                        )
                }

        private fun createDownstreamFailureScenario() =
                scenario("downstream-failure") {
                        val now = Instant.now()
                        description("Downstream payment service failure")
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))

                        monitor(
                                MonitorResponse(
                                        id = 23456,
                                        name = "Order Service Error Rate",
                                        type = "metric alert",
                                        query =
                                                "sum:trace.order-service.request.errors{env:prod}.as_rate() > 5",
                                        tags = listOf("service:order-service")
                                )
                        )

                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                id = "log-1",
                                                timestamp = now,
                                                service = "order-service",
                                                status = "error",
                                                message =
                                                        "Failed to connect to payment-service: Connection timeout",
                                                attributes =
                                                        mapOf(
                                                                "downstream.service" to
                                                                        "payment-service"
                                                        )
                                        ),
                                        LogEntry(
                                                id = "log-2",
                                                timestamp = now,
                                                service = "order-service",
                                                status = "warn",
                                                message =
                                                        "CircuitBreaker 'payment-service' state changed: CLOSED -> OPEN",
                                                attributes =
                                                        mapOf("circuit.name" to "payment-service")
                                        )
                                )
                        )
                }

        private fun createAuthConfigChangeScenario() =
                scenario("auth-config-change") {
                        val now = Instant.now()
                        description("Auth config change causing 401s")
                        incidentStart(now.minus(30, ChronoUnit.MINUTES))

                        incidentLogs(
                                listOf(
                                        LogEntry(
                                                id = "log-1",
                                                timestamp = now,
                                                service = "order-service",
                                                status = "error",
                                                message = "Authentication failed: 401 Unauthorized",
                                                attributes = mapOf("http.status_code" to 401)
                                        ),
                                        LogEntry(
                                                id = "log-2",
                                                timestamp = now,
                                                service = "auth-service",
                                                status = "warn",
                                                message =
                                                        "Token validation failed: issuer mismatch. Token issuer: https://auth.example.com, Expected: https://auth-v2.example.com",
                                                attributes =
                                                        mapOf(
                                                                "validation.error" to
                                                                        "issuer_mismatch"
                                                        )
                                        )
                                )
                        )

                        events(
                                listOf(
                                        EventEntry(
                                                id = 1,
                                                title = "Config Update: auth-service-config",
                                                text =
                                                        "Updated allowed_issuers - removed https://auth.example.com",
                                                dateHappened = now.minus(5, ChronoUnit.MINUTES),
                                                source = "kubectl",
                                                tags = listOf("config", "auth-service")
                                        )
                                )
                        )
                }

        // =========================================================================
        // Helper: Create test DICE client
        // =========================================================================

        private fun createTestDiceClient(): DiceClient {
                // For integration tests, use real DICE client pointing to test server
                // For unit tests, this could be a mock
                return DiceClient(
                        diceServerUrl = System.getenv("DICE_SERVER_URL") ?: "http://localhost:8080"
                )
        }

        companion object {
                @JvmStatic
                @AfterAll
                fun generateReports() {
                        // Generate reports after all tests complete
                        globalTestReportCollector.generateReports("test-reports")
                        val summaryFile = java.io.File("test-reports", "test-summary.txt")
                        summaryFile.parentFile?.mkdirs()
                        globalTestReportCollector.generateSummary(summaryFile)
                }
        }
}
