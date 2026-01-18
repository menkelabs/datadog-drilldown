# Spring Boot Embabel Dice Integration Module - Implementation Plan

## Overview

This document outlines the plan to create a fully-implemented Spring Boot module that integrates with **Embabel** (an AI agent framework) and **Dice** for providing chat-based incident Root Cause Analysis (RCA) advice. The module will work alongside the existing `dd_rca` Python module.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    embabel-dice-rca Module                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌───────────────┐    ┌─────────────────┐  │
│  │    Dice      │───▶│   Embabel     │───▶│   RCA Engine    │  │
│  │  Ingestion   │    │   AI Agent    │    │   (Analysis)    │  │
│  └──────────────┘    └───────────────┘    └─────────────────┘  │
│         │                   │                      │            │
│         ▼                   ▼                      ▼            │
│  ┌──────────────┐    ┌───────────────┐    ┌─────────────────┐  │
│  │  Event Bus   │    │ Chat Session  │    │ Datadog Client  │  │
│  │              │    │   Manager     │    │   (Interface)   │  │
│  └──────────────┘    └───────────────┘    └─────────────────┘  │
│                                                   │             │
└───────────────────────────────────────────────────│─────────────┘
                                                    │
                                          ┌─────────▼─────────┐
                                          │  Mock Datadog     │
                                          │  (for tests)      │
                                          └───────────────────┘
```

## Module Structure

```
embabel-dice-rca/
├── pom.xml                          # Maven build file
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── rca/
│   │   │               ├── EmbabelDiceRcaApplication.kt
│   │   │               ├── config/
│   │   │               │   ├── DatadogConfig.kt
│   │   │               │   └── EmbabelConfig.kt
│   │   │               ├── domain/
│   │   │               │   ├── Symptom.kt
│   │   │               │   ├── LogCluster.kt
│   │   │               │   ├── Candidate.kt
│   │   │               │   ├── Report.kt
│   │   │               │   └── IncidentContext.kt
│   │   │               ├── datadog/
│   │   │               │   ├── DatadogClient.kt (interface)
│   │   │               │   ├── DatadogClientImpl.kt
│   │   │               │   └── dto/
│   │   │               │       ├── LogEntry.kt
│   │   │               │       ├── MetricResponse.kt
│   │   │               │       └── SpanEntry.kt
│   │   │               ├── analysis/
│   │   │               │   ├── LogAnalyzer.kt
│   │   │               │   ├── MetricAnalyzer.kt
│   │   │               │   ├── ApmAnalyzer.kt
│   │   │               │   └── ScoringEngine.kt
│   │   │               ├── dice/
│   │   │               │   ├── DiceIngestionService.kt
│   │   │               │   ├── DiceEventHandler.kt
│   │   │               │   └── model/
│   │   │               │       ├── IngestionEvent.kt
│   │   │               │       └── AlertTrigger.kt
│   │   │               └── agent/
│   │   │                   ├── RcaAgent.kt (Embabel agent)
│   │   │                   ├── ChatAdvisor.kt
│   │   │                   ├── actions/
│   │   │                   │   ├── AnalyzeIncidentAction.kt
│   │   │                   │   ├── InvestigateLogsAction.kt
│   │   │                   │   └── SuggestRemediation.kt
│   │   │                   └── prompts/
│   │   │                       └── RcaPrompts.kt
│   │   └── resources/
│   │       ├── application.yml
│   │       └── prompts/
│   │           └── rca-system-prompt.txt
│   └── test/
│       ├── kotlin/
│       │   └── com/
│       │       └── example/
│       │           └── rca/
│       │               ├── integration/
│       │               │   ├── DiceIngestionIntegrationTest.kt
│       │               │   ├── ChatAdviceIntegrationTest.kt
│       │               │   └── EndToEndRcaTest.kt
│       │               ├── mock/
│       │               │   └── MockDatadogClient.kt
│       │               └── fixtures/
│       │                   └── TestScenarios.kt
│       └── resources/
│           ├── application-test.yml
│           └── fixtures/
│               ├── sample-logs.json
│               ├── sample-metrics.json
│               └── sample-spans.json
└── PLAN.md
```

## Key Components

### 1. Domain Models

Mirror the Python `dd_rca` models in Kotlin:

- **Symptom**: Represents detected anomalies (latency, error_rate, log_signature)
- **LogCluster**: Grouped log patterns with fingerprinting
- **Candidate**: Ranked root cause candidates with evidence
- **Report**: Complete RCA analysis output
- **IncidentContext**: Time windows, scope, and metadata

### 2. Datadog Client Interface

```kotlin
interface DatadogClient {
    fun getMonitor(monitorId: Long): MonitorResponse
    fun queryMetrics(query: String, start: Instant, end: Instant): MetricResponse
    fun searchLogs(query: String, start: Instant, end: Instant): List<LogEntry>
    fun searchSpans(query: String, start: Instant, end: Instant): List<SpanEntry>
    fun searchEvents(start: Instant, end: Instant, tags: String?): EventResponse
}
```

### 3. Dice Integration

Dice ingestion service handles:
- Alert triggers (monitor alerts, threshold breaches)
- Log stream events
- Metric anomaly notifications
- Manual incident reports

```kotlin
@Service
class DiceIngestionService(
    private val eventBus: ApplicationEventPublisher,
    private val rcaAgent: RcaAgent
) {
    fun ingestAlert(trigger: AlertTrigger): IngestionResult
    fun ingestLogStream(logs: List<LogEntry>): IngestionResult
    fun ingestMetricAnomaly(anomaly: MetricAnomaly): IngestionResult
}
```

### 4. Embabel AI Agent

The RCA Agent uses Embabel's agent framework to:
- Analyze incident data using AI reasoning
- Provide contextual chat responses
- Suggest remediation actions
- Generate human-readable reports

```kotlin
@EmbabelAgent
class RcaAgent(
    private val analysisEngine: AnalysisEngine,
    private val chatAdvisor: ChatAdvisor
) {
    @Action("analyze-incident")
    fun analyzeIncident(context: IncidentContext): Report

    @Action("investigate-logs")
    fun investigateLogs(query: String, timeWindow: TimeWindow): List<LogCluster>

    @Action("chat-advice")
    fun provideChatAdvice(question: String, context: IncidentContext): ChatResponse
}
```

### 5. Chat Advisor

Natural language interface for incident investigation:

```kotlin
@Service
class ChatAdvisor(
    private val embabelClient: EmbabelClient,
    private val sessionManager: ChatSessionManager
) {
    fun startSession(incidentId: String): ChatSession
    fun sendMessage(sessionId: String, message: String): ChatResponse
    fun getRecommendations(sessionId: String): List<Recommendation>
}
```

## Test Strategy

### Integration Tests (Real tests, Datadog mocked)

1. **DiceIngestionIntegrationTest**
   - Test alert ingestion triggers analysis
   - Test log stream processing
   - Test metric anomaly handling

2. **ChatAdviceIntegrationTest**
   - Test chat session creation
   - Test multi-turn conversations
   - Test context-aware responses

3. **EndToEndRcaTest**
   - Full flow: ingest → analyze → chat advice
   - Multiple incident scenarios
   - Verify report generation

### Mock Datadog Implementation

```kotlin
class MockDatadogClient : DatadogClient {
    private val scenarios = mutableMapOf<String, DatadogScenario>()

    fun loadScenario(name: String, scenario: DatadogScenario)
    fun setActiveScenario(name: String)

    // Returns data from active scenario
    override fun searchLogs(...) = activeScenario.logs
    override fun queryMetrics(...) = activeScenario.metrics
    // etc.
}
```

### Test Scenarios

1. **High Latency Incident**
   - Symptoms: P95 latency spike
   - Root cause: Database connection pool exhaustion
   - Expected advice: Check connection limits, add pooling

2. **Error Rate Spike**
   - Symptoms: 5xx errors increase
   - Root cause: Downstream service unavailable
   - Expected advice: Check dependency health, add circuit breaker

3. **Memory Pressure**
   - Symptoms: OOM events, GC pauses
   - Root cause: Memory leak in request handling
   - Expected advice: Analyze heap dumps, check for unclosed resources

## Implementation Steps

1. **Phase 1: Project Setup**
   - Create Maven project with Spring Boot 3.2+
   - Add Embabel SDK dependencies
   - Configure Kotlin and required plugins

2. **Phase 2: Core Domain**
   - Implement domain models
   - Create Datadog client interface
   - Build mock implementation with scenarios

3. **Phase 3: Analysis Engine**
   - Port analysis logic from Python module
   - Implement log clustering
   - Create scoring engine

4. **Phase 4: Dice Integration**
   - Implement ingestion service
   - Create event handlers
   - Build event routing

5. **Phase 5: Embabel Agent**
   - Configure Embabel client
   - Implement RCA agent with actions
   - Create chat advisor

6. **Phase 6: Integration Tests**
   - Write comprehensive integration tests
   - Create test scenarios
   - Verify end-to-end flows

## Dependencies

```xml
<!-- Spring Boot -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<!-- Embabel SDK -->
<dependency>
    <groupId>com.embabel</groupId>
    <artifactId>embabel-spring-boot-starter</artifactId>
    <version>LATEST</version>
</dependency>

<!-- Kotlin -->
<dependency>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-stdlib</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
</dependency>
```

## Configuration

```yaml
# application.yml
spring:
  application:
    name: embabel-dice-rca

embabel:
  api-key: ${EMBABEL_API_KEY}
  model: gpt-4
  temperature: 0.3

datadog:
  api-key: ${DD_API_KEY}
  app-key: ${DD_APP_KEY}
  site: ${DD_SITE:datadoghq.com}

dice:
  ingestion:
    enabled: true
    batch-size: 100
    flush-interval: 5s
```

## Success Criteria

1. All integration tests pass with mocked Datadog
2. Tests cover ingestion → analysis → chat advice flow
3. Chat responses are contextually relevant
4. Module can run standalone or alongside Python module
5. Clear separation of concerns (Datadog interface allows real/mock swap)
