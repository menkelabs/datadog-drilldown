# DICE-Based RCA Testing Framework

## Overview

This document describes how to test the DICE-based Root Cause Analysis system by:
1. **Pushing prior knowledge** into DICE (system architecture, past incidents, failure patterns)
2. **Simulating new alerts** that contain only symptoms (not root causes)
3. **Verifying conclusions** that DICE + the RCA agent correctly identify the root cause

## The Problem

In real incidents, alerts show **symptoms**, not root causes:

| Alert (Symptom) | Hidden Root Cause |
|-----------------|-------------------|
| "API latency > 500ms" | Database connection pool exhausted |
| "Error rate > 5%" | Downstream service failure |
| "401 errors spiking" | Config change removed OAuth issuer |
| "Intermittent 503s" | DNS failure on specific K8s node |

The RCA system must **investigate** to find the root cause using:
- Prior knowledge (architecture, dependencies, past incidents)
- Real-time data (metrics, logs, traces, events)
- Reasoning (correlating evidence to identify causation)

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         DICE Engine                              │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ Prior Knowledge  │  │  Alert/Evidence  │  │   Reasoning   │  │
│  │  - Architecture  │  │  - Alert details │  │  - Correlate  │  │
│  │  - Dependencies  │  │  - Metrics       │  │  - Conclude   │  │
│  │  - Past incidents│  │  - Logs          │  │  - Recommend  │  │
│  │  - Runbooks      │  │  - Traces        │  │               │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Conclusion     │
                    │  "Root cause is  │
                    │   DB pool        │
                    │   exhaustion"    │
                    └──────────────────┘
```

## Prior Knowledge Types

### 1. System Architecture
What services exist and how they relate:

```kotlin
SystemArchitecture(
    name = "E-Commerce Platform",
    services = listOf(
        ServiceInfo(
            name = "api-service",
            description = "Main API gateway",
            dependencies = listOf("postgres", "redis", "order-service"),
            critical = true
        ),
        ServiceInfo(
            name = "postgres",
            description = "Primary database",
            type = "database"
        )
    )
)
```

### 2. Service Dependencies
How services communicate and what's critical:

```kotlin
ServiceDependency(
    from = "api-service",
    to = "postgres",
    type = "database",
    critical = true,
    notes = "HikariCP pool, max 100 connections"
)
```

### 3. Failure Patterns
Known failure modes and their signatures:

```kotlin
FailurePattern(
    id = "fp-db-pool",
    name = "Database Connection Pool Exhaustion",
    symptoms = listOf(
        "High latency on database endpoints",
        "TimeoutError: Connection pool exhausted",
        "HikariPool: Connection not available"
    ),
    rootCause = "Pool saturated due to slow queries or leaks",
    resolution = "Check slow queries, increase pool size"
)
```

### 4. Past Incidents
Historical incidents with lessons learned:

```kotlin
PastIncident(
    id = "INC-2024-001",
    title = "API Latency Due to DB Pool",
    symptoms = listOf("P95 latency 5000ms", "Pool at 100%"),
    rootCause = "New query holding connections too long",
    lessonsLearned = listOf(
        "Test query performance in staging",
        "Monitor connection pool metrics"
    )
)
```

### 5. Runbooks
Investigation procedures:

```kotlin
Runbook(
    title = "High Latency Investigation",
    trigger = "P95 latency > 500ms",
    steps = listOf(
        "Check service CPU and memory",
        "Check database connection pool",
        "Check for slow queries",
        "Check recent deployments"
    )
)
```

## Test Structure

### Step 1: Load Prior Knowledge

```kotlin
val priorKnowledge = PriorKnowledge(
    architecture = myArchitecture,
    dependencies = myDependencies,
    failurePatterns = myPatterns,
    pastIncidents = myIncidents,
    runbooks = myRunbooks
)

testFramework.loadPriorKnowledge(contextId, priorKnowledge)
```

### Step 2: Setup Mock Datadog Data

```kotlin
mockDatadog.loadScenario("db-pool-exhaustion", scenario {
    incidentMetrics(MetricResponse(
        series = listOf(MetricSeries(
            metric = "jvm.db.pool.active",
            pointlist = listOf(MetricPoint(now, 100.0)) // saturated
        ))
    ))
    
    incidentLogs(listOf(
        LogEntry(
            message = "TimeoutError: Connection pool exhausted",
            attributes = mapOf("error.type" to "TimeoutError")
        )
    ))
})
```

### Step 3: Simulate Alert

```kotlin
val alert = TestAlert(
    name = "API P95 Latency Alert",
    service = "api-service",
    message = "P95 latency exceeded 500ms. Current: 2450ms",
    metricsToQuery = listOf(
        "p95:trace.api-service.request.duration{env:prod}",
        "avg:jvm.db.pool.active{service:api-service}"
    ),
    logQueries = listOf("service:api-service status:error")
)

val result = testFramework.simulateAlert(contextId, alert)
```

### Step 4: Verify Conclusion

```kotlin
val verification = testFramework.verifyConclusion(
    result,
    ExpectedRootCause(
        keywords = listOf("connection pool", "database", "exhausted"),
        component = "database",
        causeType = "pool exhaustion"
    )
)

assertTrue(verification.passed)
```

## Test Scenarios

### Scenario 1: Database Pool Exhaustion

**Alert**: "API latency > 500ms"

**Prior Knowledge**:
- api-service depends on postgres via HikariCP
- Past incident: Pool exhaustion caused by slow queries
- Runbook: Check pool metrics when latency high

**Mock Data**:
- `jvm.db.pool.active`: 100 (at max)
- `jvm.db.pool.timeout`: 200+
- Logs: "TimeoutError: Connection pool exhausted"

**Expected Conclusion**: Database connection pool is exhausted

---

### Scenario 2: Downstream Service Failure

**Alert**: "Order service error rate > 5%"

**Prior Knowledge**:
- order-service depends on payment-service
- Circuit breaker configured for payment-service
- Past incident: Payment DB outage caused cascade

**Mock Data**:
- payment-service errors: 100%
- Circuit breaker: OPEN
- Logs: "Failed to connect to payment-service"

**Expected Conclusion**: payment-service is down, causing cascade

---

### Scenario 3: Config Change Auth Failure

**Alert**: "Order service 401 errors spiking"

**Prior Knowledge**:
- All services use auth-service for token validation
- Past incident: Config change invalidated tokens
- Pattern: When auth-service has no errors but clients see 401s, check config

**Mock Data**:
- 401 errors across multiple services
- auth-service logs: "issuer_mismatch"
- Event: "Config Update: auth-service-config" 5 mins ago

**Expected Conclusion**: Config change removed valid OAuth issuer

---

### Scenario 4: Node-Specific DNS Failure

**Alert**: "Checkout service intermittent 503s (8%)"

**Prior Knowledge**:
- NodeLocal DNSCache runs as DaemonSet
- Pods on same node share DNS cache
- Pattern: Intermittent errors often indicate partial failure

**Mock Data**:
- Errors only from pods on worker-node-3
- DNS lookup errors on worker-node-3
- Event: "node-local-dns OOMKilled on worker-node-3"

**Expected Conclusion**: DNS cache failure on specific node

## Running Tests

### Unit Tests (Mock DICE)

```bash
./mvnw test -Dtest=DiceRcaIntegrationTest
```

### Integration Tests (Real DICE)

```bash
# Start DICE server
cd dice-server && ./mvnw spring-boot:run

# Run tests
DICE_SERVER_URL=http://localhost:8080 ./mvnw test -Dtest=DiceRcaIntegrationTest
```

## Adding New Test Scenarios

1. **Define prior knowledge** relevant to the failure mode
2. **Create mock Datadog scenario** with realistic data
3. **Define expected root cause** with keywords and components
4. **Write test** using the framework

```kotlin
@Test
fun `should identify new failure mode`() {
    val contextId = "test-new-scenario"
    
    // 1. Prior knowledge
    val knowledge = PriorKnowledge(
        failurePatterns = listOf(myNewPattern),
        pastIncidents = listOf(relevantIncident)
    )
    testFramework.loadPriorKnowledge(contextId, knowledge)
    
    // 2. Mock data
    mockDatadog.loadScenario("new-scenario", myScenario)
    
    // 3. Alert
    val alert = TestAlert(name = "My Alert", message = "Symptom description")
    val result = testFramework.simulateAlert(contextId, alert)
    
    // 4. Verify
    val verification = testFramework.verifyConclusion(result, 
        ExpectedRootCause(keywords = listOf("expected", "keywords")))
    assertTrue(verification.passed)
}
```

## Key Insight

The power of this approach is that:

1. **DICE learns from prior knowledge** - It knows what failure patterns look like
2. **Evidence is gathered systematically** - Metrics, logs, traces, events
3. **Conclusions are verifiable** - We can check if the right root cause was identified
4. **Edge cases are testable** - Tricky scenarios like "auth-service has no errors but clients see 401s"

This enables confident deployment of automated RCA that augments (not replaces) human investigation.
