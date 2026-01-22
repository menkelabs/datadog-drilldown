# Architecture Documentation

This directory contains C4 model PlantUML diagrams documenting the Embabel Dice RCA system architecture.

## Diagrams

### C4 Model Diagrams

| Level | Diagram | Description |
|-------|---------|-------------|
| 1 - Context | [c4-context.puml](c4-context.puml) | System context showing users and external systems |
| 2 - Container | [c4-container.puml](c4-container.puml) | High-level containers within the system |
| 3 - Component | [c4-component.puml](c4-component.puml) | Components within the Embabel Agent container |
| 3 - Component | [c4-mcp-tools.puml](c4-mcp-tools.puml) | Detailed view of Datadog MCP tools |
| Deployment | [c4-deployment.puml](c4-deployment.puml) | Deployment topology |

### Sequence Diagrams

| Diagram | Description |
|---------|-------------|
| [sequence-investigation.puml](sequence-investigation.puml) | Full incident investigation flow |
| [sequence-dice-ingestion.puml](sequence-dice-ingestion.puml) | Alert ingestion via Dice |
| [sequence-testing.puml](sequence-testing.puml) | DICE-based RCA testing flow with prior knowledge and YAML scenarios |

### Workflow Diagrams

| Diagram | Description |
|---------|-------------|
| [agent-workflow.puml](agent-workflow.puml) | Embabel agent action flow with conditions |

## Rendering Diagrams

### Option 1: PlantUML Server

```bash
# Using public server
curl -o diagram.png "http://www.plantuml.com/plantuml/png/$(cat c4-context.puml | python3 -c 'import sys,zlib,base64; print(base64.urlsafe_b64encode(zlib.compress(sys.stdin.read().encode())).decode())')"
```

### Option 2: Local PlantUML

```bash
# Install PlantUML
brew install plantuml  # macOS
apt-get install plantuml  # Ubuntu

# Render all diagrams
plantuml -tpng *.puml
plantuml -tsvg *.puml
```

### Option 3: IDE Plugins

- **IntelliJ IDEA**: PlantUML Integration plugin
- **VS Code**: PlantUML extension
- **Cursor**: PlantUML extension

### Option 4: Online Editor

Visit [PlantUML Web Server](http://www.plantuml.com/plantuml/uml/) and paste the diagram content.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         External Systems                             │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │   Datadog   │  │ LLM Provider│  │  Alerting   │  │    Chat    │ │
│  │  (Logs,     │  │  (OpenAI,   │  │ (PagerDuty, │  │  (Slack,   │ │
│  │   Metrics,  │  │  Anthropic, │  │  OpsGenie)  │  │   Teams)   │ │
│  │   APM)      │  │  Ollama)    │  │             │  │            │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬──────┘ │
└─────────┼────────────────┼────────────────┼───────────────┼────────┘
          │                │                │               │
          ▼                ▼                ▼               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Embabel Dice RCA System                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              IncidentInvestigatorAgent (@Agent)              │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐│   │
│  │  │categorize│→│  parse   │→│ collect  │→│    analyze       ││   │
│  │  │ Incident │ │ Request  │ │ Evidence │ │   RootCause      ││   │
│  │  └──────────┘ └──────────┘ └────┬─────┘ └────────┬─────────┘│   │
│  │                                 │                │          │   │
│  │                          ┌──────▼──────┐  ┌──────▼───────┐  │   │
│  │                          │  critique   │→ │  generate    │  │   │
│  │                          │  Analysis   │  │   Report     │  │   │
│  │                          └─────────────┘  └──────────────┘  │   │
│  └──────────────────────────────────┬──────────────────────────┘   │
│                                     │                               │
│  ┌──────────────────────────────────▼──────────────────────────┐   │
│  │                  Datadog MCP Tools (@LlmTool)                │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ │   │
│  │  │search_logs │ │query_metric│ │search_trace│ │get_events │ │   │
│  │  └────────────┘ └────────────┘ └────────────┘ └───────────┘ │   │
│  │  ┌────────────┐ ┌────────────┐                              │   │
│  │  │get_monitor │ │compare_    │                              │   │
│  │  │            │ │periods     │                              │   │
│  │  └────────────┘ └────────────┘                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────┐   │
│  │ DiceIngestion    │  │  DatadogClient   │  │ Analysis Engine │   │
│  │ Service          │  │  (HTTP Client)   │  │ (Clustering,    │   │
│  │                  │  │                  │  │  Scoring)       │   │
│  └──────────────────┘  └──────────────────┘  └─────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### 1. Embabel Agent Framework
- Uses `@Agent`, `@Action`, `@Condition`, `@AchievesGoal` annotations
- Automatic workflow planning based on conditions
- Self-critique loop for quality assurance

### 2. MCP Tools Pattern
- Tools annotated with `@LlmTool` are auto-discovered
- LLM decides which tools to call based on context
- Structured responses (not raw JSON) for better reasoning

### 3. Datadog Client Abstraction
- Interface allows swapping real/mock implementations
- Mock client uses scenario-based test data
- Enables real integration tests without Datadog credentials

### 4. Dice Ingestion
- Event-driven architecture using Spring events
- Correlates incoming alerts with existing incidents
- Automatic analysis triggering based on severity

### 5. DICE-Based Testing Framework
- **DiceKnowledgeTestFramework**: Orchestrates prior knowledge loading, alert simulation, and conclusion verification
- **Prior Knowledge Types**: SystemArchitecture, ServiceDependencies, FailurePatterns, PastIncidents, Runbooks, SLOs
- **YAML Scenarios**: 20+ realistic incident scenarios (database-pool-exhaustion, downstream-failure, kubernetes-oom, etc.)
- **MockDatadogClient**: Scenario-based mock that returns data from YAML files
- **Verification**: Tests verify correct root cause identification with keyword matching and coverage thresholds
