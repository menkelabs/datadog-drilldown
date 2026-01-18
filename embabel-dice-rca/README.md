# Embabel Dice RCA Agent

AI-powered Root Cause Analysis for production incidents using Datadog telemetry and the Embabel Agent framework.

## Overview

This module provides an intelligent incident investigation assistant that:
- Collects evidence from Datadog (logs, metrics, APM traces, events)
- Uses AI reasoning to identify root cause candidates
- Provides actionable recommendations via chat interface
- Integrates with Dice for data ingestion pipelines

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- OpenAI API key (or Anthropic/Ollama for local models)
- Datadog API credentials

### Installation

```bash
cd embabel-dice-rca
mvn clean install
```

### Configuration

Set environment variables:

```bash
# Required: LLM Provider (at least one)
export OPENAI_API_KEY="sk-..."
# Or for Anthropic
export ANTHROPIC_API_KEY="sk-ant-..."
# Or for local Ollama
export OLLAMA_BASE_URL="http://localhost:11434"

# Required: Datadog credentials
export DD_API_KEY="your-datadog-api-key"
export DD_APP_KEY="your-datadog-app-key"
export DD_SITE="datadoghq.com"  # or datadoghq.eu, etc.
```

### Running the Agent

```bash
mvn spring-boot:run
```

The interactive shell will start:

```
========================================
Embabel RCA Agent - Incident Investigator
========================================

Describe your incident and I'll help investigate the root cause.

rca> 
```

---

## Real-World Engineering Scenarios

### Scenario 1: API Latency Spike After Deployment

**Situation**: Your team just deployed version 2.5.0 of the checkout service. Within 10 minutes, PagerDuty fires an alert: "P95 latency > 500ms for checkout-service".

**Using the RCA Agent**:

```
rca> The checkout-service in production is showing P95 latency of 800ms after 
     deploying v2.5.0 about 15 minutes ago. Normal latency is around 50ms.
```

**What the agent does**:

1. **Categorizes** the incident as `LATENCY`
2. **Collects evidence** from Datadog:
   - Error logs from checkout-service in prod
   - Recent deploy events
   - APM traces showing slow spans
   - Downstream dependency latencies
3. **Analyzes** patterns:
   - Finds new error pattern: "Connection pool exhausted after 5000ms"
   - Correlates with deploy event 15 minutes ago
   - Identifies postgres dependency showing 10x latency increase
4. **Generates report**:

```
## Investigation Report

**Summary**: The latency spike is caused by database connection pool exhaustion 
introduced in v2.5.0. The new code path makes 3x more DB calls per request, 
depleting the connection pool under normal load.

**Root Cause**: Connection pool size (10) insufficient for new query pattern
- Confidence: 87%
- Evidence: 847 "Connection pool exhausted" errors in last 15 minutes
- Correlation: Deploy event for v2.5.0 occurred 2 minutes before first error

**Recommendations**:
1. IMMEDIATE: Rollback to v2.4.9 to restore service
2. SHORT-TERM: Increase connection pool size to 50 in v2.5.1
3. LONG-TERM: Add connection pool metrics to dashboard, set alert at 80% utilization

**Next Steps**:
- Check if other services share the same DB connection pool
- Review v2.5.0 PR for new database queries
- Add load test for connection pool behavior
```

---

### Scenario 2: Cascading Failure from Third-Party API

**Situation**: Users report checkout failures. Error rate jumped from 0.1% to 15%. Multiple services showing issues.

**Using the RCA Agent**:

```
rca> We're seeing 15% error rate across checkout flow. Users getting 500 errors.
     Started about 20 minutes ago. Affects payment-service, order-service, 
     and notification-service in production.
```

**Agent Investigation**:

```
## Investigation Report

**Summary**: Cascading failure triggered by payment-gateway (Stripe) timeout. 
The payment-service lacks circuit breaker, causing thread pool exhaustion 
that propagated to order-service and notification-service.

**Root Cause**: External dependency failure (payment-gateway)
- Confidence: 92%
- Evidence: 
  - 2,341 "Connection timeout: payment-gateway:443" errors
  - Stripe status page shows degraded performance starting 22 min ago
  - payment-service thread pool at 100% utilization
  - order-service requests backing up (queue depth: 15,000)

**Failure Chain**:
1. payment-gateway (Stripe) latency increased to 30s
2. payment-service threads blocked waiting for responses
3. order-service couldn't get payment confirmations, started retrying
4. notification-service queue backed up waiting for order completions

**Recommendations**:
1. IMMEDIATE: Enable circuit breaker for payment-gateway calls
2. IMMEDIATE: Add fallback payment processing or graceful degradation
3. SHORT-TERM: Implement request timeouts (5s max for external calls)
4. LONG-TERM: Add bulkhead pattern to isolate payment failures

**Monitoring Gaps Identified**:
- No alert on external API latency
- Missing circuit breaker metrics
- Thread pool utilization not monitored
```

---

### Scenario 3: Memory Leak Causing OOM Crashes

**Situation**: Kubernetes pods for user-service keep getting OOMKilled. Happens every 4-6 hours since last week's release.

**Using the RCA Agent**:

```
rca> user-service pods in production are getting OOMKilled every 4-6 hours.
     Started happening after last Tuesday's release. Memory grows from 512MB 
     to 2GB before crash. Service is user-service, env is prod.
```

**Agent Investigation**:

```
## Investigation Report

**Summary**: Memory leak in user-service caused by unclosed HTTP connections 
in the new UserProfileCache introduced in release 3.2.0.

**Root Cause**: Resource leak in UserProfileCache
- Confidence: 78%
- Evidence:
  - Heap dump analysis shows 45,000 HttpConnection objects
  - GC logs show increasing old-gen before each OOM
  - Pattern: "GC overhead limit exceeded" appears 10-15 min before OOM
  - Release 3.2.0 introduced UserProfileCache with HTTP client

**Memory Growth Pattern**:
- Hour 0: 512MB (healthy)
- Hour 2: 890MB (+74%)
- Hour 4: 1.4GB (+57%)
- Hour 6: 2.0GB → OOMKilled

**Log Patterns Found**:
- NEW: "Creating new HTTP client for cache refresh" (15,000 occurrences/hour)
- MISSING: "Closing HTTP client" (0 occurrences)

**Recommendations**:
1. IMMEDIATE: Increase pod memory limit to 4GB (buys time)
2. IMMEDIATE: Increase restart frequency to every 2 hours (temporary)
3. FIX: Close HTTP clients after use in UserProfileCache.refresh()
4. FIX: Use connection pooling instead of creating new clients

**Code Review Suggestion**:
Look at UserProfileCache.java lines 45-67 where HTTP client is instantiated.
The client.close() call is missing in the finally block.
```

---

### Scenario 4: Intermittent Database Deadlocks

**Situation**: Sporadic 500 errors on order creation. Happens 10-20 times per hour, no clear pattern.

**Using the RCA Agent**:

```
rca> We're seeing intermittent 500 errors on POST /orders endpoint.
     About 10-20 per hour, random timing. Error logs mention "deadlock detected".
     This is order-service in production, using PostgreSQL.
```

**Agent Investigation**:

```
## Investigation Report

**Summary**: Database deadlocks occurring when concurrent orders update 
inventory and payment records in different order. Classic lock ordering issue.

**Root Cause**: Transaction lock ordering conflict
- Confidence: 85%
- Evidence:
  - 127 "deadlock detected" errors in last 6 hours
  - Deadlocks occur between inventory_update and payment_record transactions
  - 73% of deadlocks involve the same two tables
  - Correlation: Deadlock frequency increases with order volume

**Deadlock Pattern**:
```
Transaction A: UPDATE inventory → UPDATE payments (waiting)
Transaction B: UPDATE payments → UPDATE inventory (waiting)
→ Deadlock!
```

**Affected Operations**:
- POST /orders (createOrder) - 89 deadlocks
- PUT /orders/{id}/cancel (cancelOrder) - 38 deadlocks

**Recommendations**:
1. FIX: Ensure consistent lock ordering (always inventory before payments)
2. FIX: Add retry logic with exponential backoff for deadlock errors
3. OPTIMIZE: Consider using SELECT FOR UPDATE SKIP LOCKED for inventory
4. MONITOR: Add deadlock counter metric and alert

**Query Analysis**:
The createOrder transaction should be refactored to:
1. Lock inventory rows first (sorted by product_id)
2. Then lock payment records
3. Use NOWAIT or short lock_timeout to fail fast
```

---

### Scenario 5: SSL Certificate Expiration

**Situation**: Suddenly all requests to api.example.com are failing with SSL errors.

**Using the RCA Agent**:

```
rca> All external requests failing with SSL certificate errors.
     Started exactly at midnight. Affects all services calling api.example.com.
     Error: "PKIX path validation failed: certificate has expired"
```

**Agent Investigation**:

```
## Investigation Report

**Summary**: SSL certificate for api.example.com expired at 00:00:00 UTC.
All services using this endpoint are failing certificate validation.

**Root Cause**: Expired SSL certificate
- Confidence: 99%
- Evidence:
  - First error at 2024-01-15T00:00:01Z (cert expiry time)
  - 45,892 "certificate has expired" errors since midnight
  - All affected services: payment-service, shipping-service, inventory-service
  - Certificate CN=api.example.com, expired 2024-01-15T00:00:00Z

**Impact**:
- 100% failure rate for external API calls
- Order processing completely blocked
- Estimated revenue impact: $XX,XXX/hour

**Recommendations**:
1. IMMEDIATE: Renew SSL certificate for api.example.com
2. IMMEDIATE: If using Let's Encrypt, run certbot renew
3. IMMEDIATE: Restart nginx/load balancer after cert update
4. PREVENT: Set up certificate expiry monitoring (alert 30 days before)
5. PREVENT: Enable auto-renewal for certificates

**Certificate Details**:
- Issuer: Let's Encrypt Authority X3
- Subject: CN=api.example.com
- Expired: 2024-01-15T00:00:00Z
- Serial: 03:AB:C1:...
```

---

## Advanced Usage

### Using with Specific Time Windows

```
rca> Investigate latency spike for checkout-service in prod 
     from 2024-01-15T14:00:00Z to 2024-01-15T14:30:00Z
```

### Investigating Specific Monitors

```
rca> Analyze monitor 12345678 that fired 2 hours ago
```

### Querying Specific Log Patterns

```
rca> Search for errors matching "connection refused" in payment-service 
     production logs from the last hour
```

### Follow-up Questions

After initial analysis, ask follow-up questions:

```
rca> What were the top 5 slowest endpoints during this incident?

rca> Show me the deployment events from the last 24 hours

rca> Are there any other services affected by the same dependency?

rca> What's the normal baseline for this metric?
```

---

## Integration with Dice

The module supports Dice data ingestion for automated incident triggering:

```kotlin
// Programmatic alert ingestion
val alert = AlertTrigger(
    id = "alert-123",
    timestamp = Instant.now(),
    alertType = AlertType.MONITOR_ALERT,
    monitorId = 12345678,
    service = "checkout-service",
    env = "prod",
    severity = AlertSeverity.HIGH
)

diceIngestionService.ingestAlert(alert)
// Automatically triggers RCA analysis
```

### Webhook Integration

Configure Datadog webhooks to send alerts to the Dice ingestion endpoint:

```yaml
# Datadog webhook payload
{
  "alert_type": "monitor_alert",
  "monitor_id": 12345678,
  "service": "checkout-service",
  "env": "prod",
  "message": "P95 latency exceeded threshold"
}
```

---

## Testing

Run tests with mocked Datadog:

```bash
mvn test
```

Tests use `MockDatadogClient` with pre-built scenarios:
- `database-latency` - Connection pool exhaustion
- `downstream-failure` - Cascading service failure
- `memory-pressure` - OOM and GC issues
- `healthy` - Baseline healthy system

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Input                              │
│            "checkout-service latency spike..."                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  IncidentInvestigatorAgent                      │
│                    (Embabel @Agent)                             │
├─────────────────────────────────────────────────────────────────┤
│  @Action categorizeIncident      →  LATENCY/ERROR/AVAILABILITY  │
│  @Action parseIncidentRequest    →  service, env, timeframe     │
│  @Action collectDatadogEvidence  →  logs, metrics, APM, events  │
│  @Action analyzeRootCause        →  candidates + reasoning      │
│  @Action critiqueAnalysis        →  quality check               │
│  @Action generateReport          →  final recommendations       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      DatadogClient                              │
│                    (REST API calls)                             │
├─────────────────────────────────────────────────────────────────┤
│  searchLogs()    - Log patterns and errors                      │
│  queryMetrics()  - Time series data                             │
│  searchSpans()   - APM traces and dependencies                  │
│  searchEvents()  - Deploys, alerts, changes                     │
│  getMonitor()    - Monitor configuration                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Datadog REST API                             │
│              (api.datadoghq.com/api/v1, v2)                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Configuration Reference

```yaml
# application.yml
embabel:
  rca:
    analysis-model: gpt-4o          # Model for deep analysis
    fast-model: gpt-4o-mini         # Model for categorization
    max-candidates: 10              # Max root cause candidates
    default-window-minutes: 30      # Incident window size
    default-baseline-minutes: 30    # Baseline comparison window

datadog:
  api-key: ${DD_API_KEY}
  app-key: ${DD_APP_KEY}
  site: datadoghq.com
  timeout-seconds: 30
  max-retries: 3
```

---

## Troubleshooting

### "No evidence collected"
- Verify Datadog credentials are correct
- Check that the service/env tags match your Datadog data
- Expand the time window if the incident is older

### "Analysis confidence low"
- Provide more specific incident details
- Include service name and environment
- Mention any recent changes or deployments

### "Datadog API rate limited"
- Reduce query frequency
- Use more specific time windows
- Enable caching for repeated queries

---

## License

Apache 2.0
