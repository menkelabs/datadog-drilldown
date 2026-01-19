# DICE Integration Setup

This document explains how to set up and run DICE for the Datadog → DICE → Slack integration.

## What is DICE?

DICE (Domain-Integrated Context Engineering) is a knowledge graph construction and reasoning library that uses proposition-based architecture. It's a Spring Boot application that provides REST API endpoints for proposition extraction and memory queries.

Source: [https://github.com/embabel/dice](https://github.com/embabel/dice)

## Architecture

DICE runs as a **separate Spring Boot service** that your Python code calls via HTTP REST API:

```
Python (dd-rca)  →  HTTP REST API  →  DICE Spring Boot Service
```

## Setup Options

### Option 1: Run DICE as a Spring Boot Service (Production)

1. **Clone and build DICE**:
   ```bash
   git clone https://github.com/embabel/dice.git
   cd dice
   ./mvnw clean install
   ```

2. **Configure DICE**:
   - DICE requires Spring Boot configuration (see DICE README)
   - Configure required beans: `PropositionRepository`, `PropositionPipeline`, etc.
   - Set up LLM providers (OpenAI/Anthropic) for proposition extraction

3. **Run DICE service**:
   ```bash
   ./mvnw spring-boot:run
   ```
   
   Or build a JAR and run:
   ```bash
   ./mvnw package
   java -jar target/dice-0.1.0-SNAPSHOT.jar
   ```

4. **Configure Python client**:
   ```bash
   export DICE_BASE_URL=http://localhost:8080
   export DICE_API_KEY=your-api-key  # If authentication enabled
   ```

### Option 2: Use Mock DICE Client (Local Development/Testing)

For local development without setting up the DICE Spring Boot service:

1. **Enable mock clients**:
   ```bash
   export USE_MOCK_CLIENTS=true
   ```

2. **Mock DICE client**:
   - The `MockDiceClient` returns fake proposition data
   - No DICE service required
   - Works for testing the full pipeline locally

See [scripts/README_MOCK_TESTING.md](../scripts/README_MOCK_TESTING.md) for details.

## DICE REST API Endpoints

According to the DICE README, the following endpoints are available:

- `GET /api/v1/contexts/{contextId}/memory` - List propositions
- `POST /api/v1/contexts/{contextId}/memory` - Create proposition directly
- `GET /api/v1/contexts/{contextId}/memory/{propositionId}` - Get proposition by ID
- `DELETE /api/v1/contexts/{contextId}/memory/{propositionId}` - Delete proposition
- `POST /api/v1/contexts/{contextId}/memory/search` - Search by similarity
- `GET /api/v1/contexts/{contextId}/memory/entity/{entityType}/{entityId}` - Get propositions by entity

**Note**: Our Python client also tries to use `POST /api/v1/contexts/{contextId}/extract` for proposition extraction. This endpoint may not be available in the current DICE version. If it fails, the pipeline gracefully continues (returns empty propositions list).

## Configuration

### DICE Service Configuration

DICE requires Spring Boot configuration. Key beans needed:

- `PropositionRepository` - Stores propositions
- `PropositionPipeline` - Processes text → propositions
- `EntityResolver` - Resolves entity mentions
- `DataDictionary` - Schema for entities/relationships

See the [DICE README](https://github.com/embabel/dice) for full configuration details.

### Python Client Configuration

Environment variables:

- `DICE_BASE_URL` - DICE service URL (default: `http://localhost:8080`)
- `DICE_API_KEY` - Optional API key for authentication
- `USE_MOCK_CLIENTS` - Set to `true` to use mock DICE client

## Testing Without DICE Service

For local development, you can use the mock DICE client:

```bash
export USE_MOCK_CLIENTS=true
python scripts/test_webhook_local.py
```

The mock client:
- Returns fake propositions for testing
- Doesn't require DICE service to be running
- Allows full pipeline testing locally

## Integration Flow

1. **Python code sends data to DICE**:
   - Report data is converted to text chunks
   - Chunks are sent to DICE for proposition extraction

2. **DICE processes and stores**:
   - Extracts propositions from chunks
   - Stores in proposition repository
   - Returns propositions to Python code

3. **Python code composes message**:
   - Uses propositions + evidence to compose Slack message
   - Posts to Slack

## Troubleshooting

### DICE Service Not Running

If DICE service is not running, you'll see connection errors. Options:
- Start the DICE service
- Use `USE_MOCK_CLIENTS=true` for local testing

### Extract Endpoint Not Found

The `/extract` endpoint may not exist in current DICE version. Our code handles this gracefully:
- Returns empty propositions list
- Pipeline continues without DICE extraction
- Slack messages are still composed from Report data

### Authentication Issues

If DICE API key authentication is enabled:
- Set `DICE_API_KEY` environment variable
- Ensure the key matches DICE server configuration

## References

- [DICE GitHub Repository](https://github.com/embabel/dice)
- [DICE README](https://github.com/embabel/dice#readme)
- [Context Engineering Needs Domain Understanding](https://medium.com/@springrod/context-engineering-needs-domain-understanding-b4387e8e4bf8)

