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

We have created **21 comprehensive YAML scenario files** located in `src/test/resources/scenarios/`. Each scenario includes complete mock data for metrics, logs, spans, and events.

### Scenario Catalog

| # | Scenario File | Category | Root Cause |
|---|--------------|----------|------------|
| 1 | `database-pool-exhaustion.yaml` | Database | HikariCP pool saturation after deployment |
| 2 | `kubernetes-oom.yaml` | Kubernetes | Memory leak causing pod OOMKilled |
| 3 | `downstream-service-failure.yaml` | Service | Cascading failure with circuit breaker |
| 4 | `redis-cache-failure.yaml` | Cache | Cache unavailable, DB fallback overload |
| 5 | `kubernetes-node-pressure.yaml` | Kubernetes | Node memory pressure, pod evictions |
| 6 | `database-deadlock.yaml` | Database | Concurrent transactions causing deadlocks |
| 7 | `kafka-consumer-lag.yaml` | Messaging | N+1 queries causing consumer lag |
| 8 | `cpu-throttling.yaml` | Kubernetes | Container CPU limits during traffic spike |
| 9 | `dns-resolution-failure.yaml` | Network | CoreDNS overwhelmed by query spike |
| 10 | `ssl-certificate-expiry.yaml` | Security | Expired TLS certificate |
| 11 | `rate-limiting.yaml` | Traffic | Abusive client consuming shared quota |
| 12 | `disk-io-saturation.yaml` | Storage | Backup job saturating disk I/O |
| 13 | `network-partition.yaml` | Network | AZ isolation causing split-brain |
| 14 | `gc-storm.yaml` | JVM | Large object allocation triggering Full GC |
| 15 | `third-party-api-degradation.yaml` | External | Stripe API degradation |
| 16 | `thread-pool-exhaustion.yaml` | Application | Blocking calls exhausting thread pool |
| 17 | `elasticsearch-cluster-issue.yaml` | Search | Data node failure, unassigned shards |
| 18 | `config-drift.yaml` | Operations | ConfigMap update not propagated to pods |
| 19 | `envoy-sidecar-failure.yaml` | Service Mesh | Envoy proxy OOM causing mesh failures |
| 20 | `secret-rotation-failure.yaml` | Security | DB credentials rotated but pods not restarted |
| 21 | (more can be added) | - | - |

### Scenario Structure

Each YAML scenario file follows this structure:

```yaml
name: scenario-name
description: Detailed description of the incident

timing:
  incident_start: "2026-01-15T12:00:00Z"
  baseline_window_minutes: 30
  incident_window_minutes: 30

scope:
  service: affected-service
  environment: prod
  # Additional scope details

monitor:
  id: 12345
  name: "Monitor Name"
  type: metric alert
  query: "datadog query"
  tags: [...]

metrics:
  - name: metric.name
    type: gauge|count
    tags: [...]
    baseline:
      value: X
      noise: Y
    incident:
      pattern: normal|spike|gradual_increase|step_change
      value: X
      noise: Y

logs:
  baseline: [...]
  incident:
    - timestamp_offset_minutes: N
      service: service-name
      status: error|warn|info
      message: "Log message"
      attributes:
        error.type: ExceptionType
        # ...

spans:
  baseline:
    - trace_id: trace-xxx
      spans:
        - span_id: span-xxx
          service: service
          resource: "endpoint"
          span_kind: server|client
          duration_ns: N
          is_error: false|true
          # ...
  incident: [...]

events:
  - id: 1001
    title: "Event Title"
    text: "Event description"
    date_happened_offset_minutes: -5
    source: deploy|kubernetes|datadog
    alert_type: info|warning|error
    tags: [...]

expected_rca:
  root_cause: "Description of root cause"
  contributing_factors: [...]
  recommended_actions: [...]
```

### Scenario Summaries

#### 1. Database Connection Pool Exhaustion
- **Symptoms**: Latency spike (50ms → 5000ms), timeout errors
- **Metrics**: `jvm.db.pool.active`, `jvm.db.pool.timeout`, `postgresql.connections.active`
- **Root Cause**: Deployment changed query patterns holding connections too long

#### 2. Kubernetes Pod OOMKilled
- **Symptoms**: Container restarts, memory at limit, GC pressure
- **Metrics**: `kubernetes.memory.usage`, `kubernetes.containers.restarts`, `jvm.gc.pause_time`
- **Root Cause**: Memory leak in OrderCache growing unbounded

#### 3. Downstream Service Failure
- **Symptoms**: Circuit breaker open, 503 errors, timeout to payment-service
- **Metrics**: `circuit_breaker.*.state`, cross-service error rates
- **Root Cause**: Payment database became unreachable

#### 4. Redis Cache Failure
- **Symptoms**: 100% cache miss rate, DB overload
- **Metrics**: `redis.clients.connected`, `app.cache.miss_rate`, `postgresql.queries.count`
- **Root Cause**: Redis pod evicted due to node memory pressure

#### 5. Kubernetes Node Pressure
- **Symptoms**: Pod evictions, scheduling failures
- **Metrics**: `kubernetes_state.node.status`, `kubernetes_state.pod.status_phase`
- **Root Cause**: Batch processor deployment increased memory requirements

#### 6. Database Deadlock
- **Symptoms**: Transaction failures, retry exhaustion
- **Metrics**: `postgresql.deadlocks`, `postgresql.locks.count`
- **Root Cause**: Deployment removed SELECT FOR UPDATE locking

#### 7. Kafka Consumer Lag
- **Symptoms**: Growing consumer lag, processing delays
- **Metrics**: `kafka.consumer.lag`, `app.kafka.message.processing_time`
- **Root Cause**: N+1 query pattern in event processor

#### 8. CPU Throttling
- **Symptoms**: Latency spikes during traffic peak
- **Metrics**: `kubernetes.cpu.cfs.throttled.seconds`, `jvm.thread.blocked_count`
- **Root Cause**: CPU limits too restrictive for marketing campaign traffic

#### 9. DNS Resolution Failure
- **Symptoms**: Intermittent UnknownHostException
- **Metrics**: `dns.lookup.errors`, `coredns.dns.request.count`
- **Root Cause**: Monitoring agent causing DNS query explosion

#### 10. SSL Certificate Expiry
- **Symptoms**: TLS handshake failures
- **Metrics**: `tls.handshake.errors`, `tls.certificate.days_until_expiry`
- **Root Cause**: Certificate renewal automation failed

#### 11-21: Additional scenarios cover rate limiting, disk I/O, network partitions, GC storms, third-party APIs, thread pools, Elasticsearch, config drift, service mesh, and secret rotation issues.

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
