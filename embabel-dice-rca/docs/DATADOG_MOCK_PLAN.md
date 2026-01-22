# Datadog API Mock Plan for Realistic Incident Drilldown

## Overview

This document outlines a comprehensive plan to mock Datadog APIs for simulating realistic incident investigation and root cause analysis (RCA) scenarios. The goal is to enable end-to-end testing of the RCA agent without requiring a live Datadog instance.

## Current State Analysis

### Existing DatadogClient Interface

The current `DatadogClient` interface supports 5 core operations:

| Method | API Endpoint | Purpose |
|--------|-------------|---------|
| `getMonitor(monitorId)` | `GET /api/v1/monitor/{id}` | Get monitor details |
| `queryMetrics(query, start, end)` | `GET /api/v1/query` | Query time-series metrics |
| `searchLogs(query, start, end)` | `POST /api/v2/logs/events/search` | Search log entries |
| `searchSpans(query, start, end)` | `POST /api/v2/apm/events/search` | Search APM traces/spans |
| `searchEvents(start, end, tags)` | `GET /api/v1/events` | Search events (deploys, alerts) |

### Existing Mock Capabilities

The `MockDatadogClient` provides scenario-based mocking with:
- Baseline vs incident period differentiation
- 4 pre-built scenarios (database latency, downstream failure, memory pressure, healthy)
- Basic log, metric, span, and event fixtures

### Gaps for Realistic Drilldown

The current mock lacks:
1. **Infrastructure metrics** (Kubernetes, hosts, containers)
2. **Database-specific metrics** (connection pools, query performance)
3. **Multi-service scenarios** (cascading failures)
4. **Query-aware responses** (different data based on the query)
5. **Realistic metric correlations** (metrics that tell a coherent story)

---

## Datadog API Categories to Mock

Based on [Datadog's Public API Collection](https://www.postman.com/datadog/datadog-s-public-workspace/collection/yp38wxl/datadog-api-collection), here are the key API categories for incident investigation:

### 1. Metrics API (Priority: HIGH)

**Endpoints:**
- `GET /api/v1/query` - Query timeseries data
- `GET /api/v1/metrics` - List active metrics
- `GET /api/v2/metrics/{metric_name}/tags` - Get metric tags

**Metrics Categories to Mock:**

#### Service Metrics (APM)
```
trace.{service}.request.duration
trace.{service}.request.hits
trace.{service}.request.errors
trace.{service}.request.error_rate
```

#### Database Metrics
```
# PostgreSQL
postgresql.connections.active
postgresql.connections.waiting  
postgresql.connections.idle
postgresql.deadlocks
postgresql.locks.count
postgresql.bgwriter.buffers_alloc
postgresql.replication.delay

# MySQL
mysql.performance.queries
mysql.performance.slow_queries
mysql.threads.connected
mysql.threads.running
mysql.innodb.row_lock_waits

# Redis
redis.clients.connected
redis.clients.blocked
redis.mem.used
redis.keys.evicted
redis.net.commands

# Generic connection pool (HikariCP, etc.)
jvm.db.pool.active
jvm.db.pool.pending
jvm.db.pool.idle
jvm.db.pool.max
jvm.db.pool.timeout
```

#### Kubernetes Metrics
```
# Pod metrics
kubernetes.pods.running
kubernetes.pods.failed
kubernetes.pods.pending
kubernetes_state.pod.status_phase
kubernetes.containers.restarts

# Container metrics
kubernetes.cpu.usage.total
kubernetes.cpu.limits
kubernetes.cpu.requests
kubernetes.memory.usage
kubernetes.memory.limits
kubernetes.memory.requests

# Node metrics
kubernetes_state.node.status
kubernetes_state.node.cpu_capacity
kubernetes_state.node.memory_capacity

# Deployment metrics
kubernetes_state.deployment.replicas
kubernetes_state.deployment.replicas_available
kubernetes_state.deployment.replicas_unavailable

# HPA metrics
kubernetes_state.hpa.current_replicas
kubernetes_state.hpa.desired_replicas
kubernetes_state.hpa.max_replicas
```

#### Infrastructure/Host Metrics
```
system.cpu.user
system.cpu.system
system.cpu.iowait
system.load.1
system.load.5
system.load.15
system.mem.used
system.mem.free
system.mem.pct_usable
system.disk.used
system.disk.free
system.io.await
system.net.bytes_rcvd
system.net.bytes_sent
```

#### JVM Metrics
```
jvm.heap.used
jvm.heap.max
jvm.heap.committed
jvm.non_heap.used
jvm.gc.pause_time
jvm.gc.count
jvm.thread.count
jvm.thread.daemon_count
```

### 2. Logs API (Priority: HIGH)

**Endpoints:**
- `POST /api/v2/logs/events/search` - Search logs
- `POST /api/v2/logs/analytics/aggregate` - Aggregate logs
- `GET /api/v2/logs/events/{log_id}` - Get specific log

**Log Categories to Mock:**

```
# Application errors
error.type: TimeoutError, SQLException, IOException, NullPointerException, OutOfMemoryError
error.message: Connection pool exhausted, Connection refused, Read timed out

# Database logs
db.statement: SELECT, INSERT, UPDATE (with timing)
db.connection: Pool exhausted, Connection timeout, Deadlock detected

# Kubernetes events as logs
kubernetes.event.reason: Unhealthy, OOMKilled, CrashLoopBackOff, FailedScheduling, Evicted
kubernetes.event.type: Warning, Normal

# HTTP access logs
@http.status_code: 200, 400, 404, 500, 502, 503, 504
@http.method: GET, POST, PUT, DELETE
@duration: response time in ms
```

### 3. APM/Traces API (Priority: HIGH)

**Endpoints:**
- `POST /api/v2/apm/events/search` - Search spans
- `GET /api/v1/trace/{trace_id}` - Get full trace

**Span Types to Mock:**

```
# Server spans (entry points)
span.kind: server
resource: GET /api/users, POST /api/orders, etc.

# Client spans (outgoing calls)
span.kind: client

# Database spans
span.type: sql
peer.service: postgres, mysql, redis
db.type: postgresql, mysql, redis
db.statement: query text

# HTTP client spans  
span.type: http
peer.service: payment-service, inventory-service
http.url: downstream URL
http.status_code: response code

# Message queue spans
span.type: queue
peer.service: kafka, rabbitmq
messaging.destination: topic/queue name
```

### 4. Events API (Priority: MEDIUM)

**Endpoints:**
- `GET /api/v1/events` - Query events
- `POST /api/v1/events` - Post event

**Event Types to Mock:**

```
# Deployment events
source: deploy, kubernetes, argocd, jenkins
title: Deployment started/completed
tags: version, environment, service

# Kubernetes events
source: kubernetes
title: Pod Created, Pod Deleted, Scaled Up/Down
alert_type: info, warning, error

# Alert events
source: datadog
alert_type: alert, recovery
title: Monitor triggered/recovered

# Config change events
source: terraform, ansible, consul
title: Configuration updated
```

### 5. Monitors API (Priority: MEDIUM)

**Endpoints:**
- `GET /api/v1/monitor/{monitor_id}` - Get monitor details
- `GET /api/v1/monitor` - List monitors
- `GET /api/v1/monitor/{monitor_id}/state` - Get monitor state

**Monitor Types to Mock:**

```
# Metric monitors
type: metric alert
query: avg:trace.request.duration{service:api} > 500

# APM monitors  
type: apm alert
query: error_rate("service:api,env:prod") > 0.05

# Log monitors
type: log alert
query: @status:error service:api > 100

# Composite monitors
type: composite
query: A && B (multiple conditions)
```

### 6. Service Level Objectives (SLOs) API (Priority: LOW)

**Endpoints:**
- `GET /api/v1/slo/{slo_id}` - Get SLO details
- `GET /api/v1/slo/{slo_id}/history` - Get SLO history

---

## Enhanced Mock Architecture

### 1. Query-Aware Response System

Instead of simple baseline/incident switching, implement query parsing:

```kotlin
interface QueryAwareMockResponse {
    fun matchesQuery(query: String): Boolean
    fun getResponse(query: String, timeRange: TimeRange): Any
}

class MetricMockRegistry {
    private val handlers = mutableMapOf<Regex, QueryAwareMockResponse>()
    
    fun register(pattern: Regex, handler: QueryAwareMockResponse)
    fun query(query: String, start: Instant, end: Instant): MetricResponse
}
```

### 2. Correlated Data Generator

Create a system that generates coherent, correlated mock data:

```kotlin
class IncidentSimulator {
    // Define incident characteristics
    data class IncidentProfile(
        val type: IncidentType,
        val startTime: Instant,
        val duration: Duration,
        val affectedServices: List<String>,
        val rootCause: RootCause,
        val severity: Severity
    )
    
    // Generate all correlated data for an incident
    fun simulate(profile: IncidentProfile): SimulatedIncident {
        return SimulatedIncident(
            metrics = generateMetrics(profile),
            logs = generateLogs(profile),
            spans = generateSpans(profile),
            events = generateEvents(profile)
        )
    }
}
```

### 3. Metric Time Series Generator

Generate realistic time series with configurable patterns:

```kotlin
class TimeSeriesGenerator {
    fun generate(config: TimeSeriesConfig): List<MetricPoint> {
        // Support patterns: normal, spike, gradual_increase, step_change
        // Add noise, seasonality, and anomalies
    }
}

data class TimeSeriesConfig(
    val baselineValue: Double,
    val baselineNoise: Double,
    val incidentValue: Double,
    val incidentPattern: Pattern,
    val transitionType: TransitionType
)
```

---

## Detailed Mock Scenarios

### Scenario 1: Database Connection Pool Exhaustion

**Symptoms:**
- API latency spike (50ms → 5000ms)
- Timeout errors in logs
- DB connection pool at max capacity

**Mock Data:**

```yaml
metrics:
  jvm.db.pool.active:
    baseline: 20 (of 100)
    incident: 100 (saturated)
  jvm.db.pool.pending:
    baseline: 0
    incident: 50+ (queued requests)
  jvm.db.pool.timeout:
    baseline: 0
    incident: 200+ (timeouts)
  trace.api.request.duration:
    baseline: 50ms
    incident: 5000ms (timeout)
  postgresql.connections.active:
    baseline: 20
    incident: 100

logs:
  - TimeoutError: Connection pool exhausted after 5000ms
  - SQLException: Unable to acquire connection from pool
  - HikariPool: Connection not available, request timed out

spans:
  - server span: GET /users, 5000ms, error=true
  - client span: postgres.query, timeout, error=true, peer.service=postgres

events:
  - Deployment: api-service v2.1.0 (5 mins before incident)
```

### Scenario 2: Kubernetes Pod OOMKilled

**Symptoms:**
- Service errors during OOM events
- Container restarts
- Memory usage spike

**Mock Data:**

```yaml
metrics:
  kubernetes.memory.usage:
    baseline: 500MB (of 1GB limit)
    incident: 1024MB (at limit)
  kubernetes.containers.restarts:
    baseline: 0
    incident: 3+
  jvm.heap.used:
    baseline: 400MB
    incident: 950MB (near max)
  jvm.gc.pause_time:
    baseline: 15ms
    incident: 5000ms (long GC pauses)

logs:
  - OutOfMemoryError: Java heap space
  - GC overhead limit exceeded
  - kubernetes event: OOMKilled
  - kubernetes event: Container restarting

spans:
  - server span: intermittent errors during restart windows
  - gaps in spans during container restarts

events:
  - kubernetes: Pod OOMKilled
  - kubernetes: Container restarted
```

### Scenario 3: Downstream Service Failure

**Symptoms:**
- Errors calling payment-service
- Circuit breaker open
- HTTP 503s from downstream

**Mock Data:**

```yaml
metrics:
  trace.api.request.errors:
    baseline: 2/min
    incident: 200/min
  trace.payment-service.request.errors:
    baseline: 0
    incident: 1000/min (downstream is source)
  circuit_breaker.payment.state:
    baseline: closed (0)
    incident: open (1)

logs:
  - ConnectionError: Failed to connect to payment-service:8080
  - HTTP 503: payment-service unavailable
  - CircuitBreaker OPEN for payment-service
  - Fallback: Using cached response

spans:
  - server span: POST /checkout, error=true, 5000ms
  - client span: http.request, payment-service, timeout, error=true
  - missing: spans from payment-service itself (it's down)

events:
  - Alert: payment-service health check failed
  - kubernetes: Pod payment-service-xyz unhealthy
```

### Scenario 4: Redis Cache Failure

**Symptoms:**
- Cache miss rate spike
- Increased DB load
- Latency increase

**Mock Data:**

```yaml
metrics:
  redis.clients.connected:
    baseline: 50
    incident: 0 (connection lost)
  redis.net.rejected_connections:
    baseline: 0
    incident: 100+
  app.cache.miss_rate:
    baseline: 5%
    incident: 100% (all misses)
  postgresql.queries.count:
    baseline: 100/min
    incident: 5000/min (fallback to DB)
  trace.api.request.duration:
    baseline: 20ms (cache hit)
    incident: 200ms (DB fallback)

logs:
  - RedisConnectionException: Unable to connect to redis:6379
  - Cache fallback: Querying database directly
  - Redis: Connection reset by peer

spans:
  - client span: redis.get, error=true, timeout
  - client span: postgres.query (increased count during incident)

events:
  - Alert: Redis connectivity lost
  - kubernetes: redis-master pod evicted
```

### Scenario 5: Kubernetes Node Pressure

**Symptoms:**
- Pod evictions
- Scheduling failures
- Resource starvation

**Mock Data:**

```yaml
metrics:
  kubernetes_state.node.status:
    baseline: Ready
    incident: NotReady / MemoryPressure
  kubernetes_state.pod.status_phase:
    baseline: Running
    incident: Evicted, Pending
  kubernetes.pods.pending:
    baseline: 0
    incident: 10+
  system.mem.pct_usable:
    baseline: 30%
    incident: 5% (pressure)

logs:
  - kubernetes event: Evicted due to node memory pressure
  - kubernetes event: FailedScheduling: Insufficient memory
  - Pod terminated: Node pressure eviction

spans:
  - gaps during pod eviction/restart
  - increased latency as pods compete for resources

events:
  - kubernetes: Node memory pressure
  - kubernetes: Pod evicted
  - kubernetes: Deployment scaled down
```

### Scenario 6: Database Deadlock

**Symptoms:**
- Specific queries failing
- Deadlock errors
- Timeout on writes

**Mock Data:**

```yaml
metrics:
  postgresql.deadlocks:
    baseline: 0
    incident: 5+
  postgresql.locks.count:
    baseline: 10
    incident: 100+
  trace.api.write_operation.errors:
    baseline: 0
    incident: 50/min

logs:
  - PSQLException: Deadlock detected
  - Transaction aborted: concurrent update conflict
  - Retry attempt 3/3 failed: deadlock

spans:
  - client span: postgres.query (UPDATE), error=true, deadlock
  - server span: POST /orders, error=true

events:
  - Alert: Database deadlock rate exceeded threshold
  - Deployment: inventory-service v1.5.0 (potential cause)
```

---

## Implementation Plan

### Phase 1: Enhanced Mock Infrastructure

1. **QueryParsingService** - Parse Datadog metric/log queries
2. **TimeSeriesGenerator** - Generate realistic time series
3. **CorrelatedDataStore** - Store related mock data together
4. **ScenarioLoader** - Load scenarios from YAML/JSON files

### Phase 2: Metric Mocks

1. Add infrastructure metrics (system.*, kubernetes.*)
2. Add database metrics (postgresql.*, mysql.*, redis.*)
3. Add JVM metrics (jvm.*)
4. Add APM metrics (trace.*)

### Phase 3: Extended Log Mocks

1. Add Kubernetes event logs
2. Add database error logs
3. Add circuit breaker state logs
4. Add structured error attributes

### Phase 4: Comprehensive Scenarios

1. Implement 6+ detailed scenarios (as outlined above)
2. Add multi-service cascading failures
3. Add partial failure scenarios
4. Add recovery/resolution patterns

### Phase 5: Tooling

1. Scenario builder CLI
2. Mock data visualization
3. Scenario comparison tools

---

## File Structure

```
embabel-dice-rca/
├── src/main/kotlin/com/example/rca/
│   ├── datadog/
│   │   ├── mock/
│   │   │   ├── EnhancedMockDatadogClient.kt
│   │   │   ├── QueryParser.kt
│   │   │   ├── TimeSeriesGenerator.kt
│   │   │   ├── MetricMockRegistry.kt
│   │   │   ├── LogMockRegistry.kt
│   │   │   └── SpanMockRegistry.kt
│   │   └── dto/
│   │       └── (existing DTOs)
│   └── ...
├── src/test/kotlin/com/example/rca/
│   ├── mock/
│   │   └── MockDatadogClient.kt (enhanced)
│   └── fixtures/
│       └── TestScenarios.kt (enhanced)
└── src/test/resources/
    └── scenarios/
        ├── database-pool-exhaustion.yaml
        ├── kubernetes-oom.yaml
        ├── downstream-failure.yaml
        ├── redis-cache-failure.yaml
        ├── node-pressure.yaml
        └── database-deadlock.yaml
```

---

## DatadogClient Interface Extensions

To support realistic drilldown, consider extending the interface:

```kotlin
interface DatadogClient {
    // Existing methods
    fun getMonitor(monitorId: Long): MonitorResponse
    fun queryMetrics(query: String, start: Instant, end: Instant): MetricResponse
    fun searchLogs(...): List<LogEntry>
    fun searchSpans(...): List<SpanEntry>
    fun searchEvents(...): EventResponse
    
    // New methods for deeper investigation
    fun listActiveMetrics(query: String? = null): List<MetricMetadata>
    fun getMetricTags(metricName: String): List<String>
    fun aggregateLogs(query: String, groupBy: List<String>): LogAggregation
    fun getTrace(traceId: String): TraceResponse
    fun listMonitors(tags: List<String>? = null): List<MonitorResponse>
    fun getSloStatus(sloId: String): SloStatus
}
```

---

## Testing Strategy

### Unit Tests
- Test each metric generator produces valid data
- Test query parsing accuracy
- Test time series patterns

### Integration Tests  
- Test full scenarios produce coherent data
- Test agent can investigate and find root cause
- Test MCP tools return expected data

### End-to-End Tests
- Complete investigation workflow with mock data
- Verify RCA report quality
- Benchmark investigation time

---

## Success Criteria

1. **Realistic Data**: Mock data is indistinguishable from real Datadog responses
2. **Coherent Stories**: All data sources tell a consistent incident story
3. **Query Support**: Common Datadog queries return appropriate data
4. **RCA Quality**: Agent can correctly identify root causes in all scenarios
5. **Extensibility**: Easy to add new scenarios and metric types

---

## References

- [Datadog API Collection (Postman)](https://www.postman.com/datadog/datadog-s-public-workspace/collection/yp38wxl/datadog-api-collection)
- [Datadog Metrics API](https://docs.datadoghq.com/api/latest/metrics/)
- [Datadog Logs API](https://docs.datadoghq.com/api/latest/logs/)
- [Datadog APM API](https://docs.datadoghq.com/api/latest/tracing/)
- [Datadog Events API](https://docs.datadoghq.com/api/latest/events/)
