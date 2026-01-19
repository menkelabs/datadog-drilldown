# Datadog RCA Assistant (Embabel + DICE)

AI-powered Root Cause Analysis (RCA) system for production incidents, leveraging **Embabel** AI agents and **DICE** (Domain-Integrated Context Engineering) for intelligent memory and reasoning.

## Architecture

The system consists of two primary Kotlin/Spring Boot modules:

1.  **`embabel-dice-rca`**: The analysis engine and AI agent. It collects telemetry from Datadog (logs, metrics, spans), performs pattern analysis, and identifies root cause candidates.
2.  **`dice-server`**: The intelligent memory and reasoning engine. It decomposes incident data into atomic facts (propositions) and provides a reasoning API to answer complex questions about incidents.

## Modules

### 1. RCA Agent (`embabel-dice-rca`)
- **Telemetry Collection**: Interfaces with Datadog REST API.
- **Analysis Engine**: Clusters logs, identifies metric anomalies, and correlates APM traces.
- **AI Agent**: Uses Embabel framework to orchestrate the investigation workflow.
- **DICE Bridge**: Pushes investigation results to the DICE server for persistent memory.

### 2. DICE Server (`dice-server`)
- **Ingestion API**: Receives raw incident data and reports.
- **Proposition Extraction**: Uses LLMs to extract atomic, factual propositions from text.
- **Reasoning Engine**: Provides semantic query capabilities over stored incident memory.
- **Persistence**: Managed factual memory of all incidents.

## Setup

### Prerequisites
- Java 21+
- Maven 3.8+
- OpenAI or Anthropic API Key
- Datadog API & App Keys

### Configuration
Set environment variables:
```bash
export OPENAI_API_KEY="sk-..."
export DD_API_KEY="..."
export DD_APP_KEY="..."
export DD_SITE="datadoghq.com"
```

### Running the Services

1. **Start the DICE Server**:
   ```bash
   cd dice-server && mvn spring-boot:run
   ```

2. **Run the RCA Agent**:
   ```bash
   cd embabel-dice-rca && mvn spring-boot:run
   ```

## Testing

### Integration Testing
The project includes a comprehensive integration test harness that simulates a Datadog incident and verifies the full flow from analysis to DICE reasoning:

```bash
cd embabel-dice-rca && mvn test -Dtest=SystemIntegrationTest
```

### Unit Testing
Each module contains unit tests for its core logic:
```bash
cd dice-server && mvn test
cd embabel-dice-rca && mvn test
```

## Documentation
- [Architecture Diagrams](embabel-dice-rca/docs/architecture/README.md)
- [PlantUML Setup Guide](docs/puml-setup.md)
