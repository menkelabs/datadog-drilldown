# Architecture Diagrams

This directory contains PlantUML architecture diagrams documenting the dd-rca system.

## Diagrams

### Context Level: `plantuml/architecture-context.puml`

High-level system context showing:
- External systems (Datadog, Slack, DICE)
- Users (DevOps Engineers, SREs)
- Main system boundaries
- Key relationships

### Container Level: `plantuml/architecture-container.puml`

Container diagram showing:
- Internal components (Webhook Server, Pipeline, Clients)
- Technology choices (Flask, Python)
- Data flow between components
- External system integrations

### Pipeline Flow: `plantuml/architecture-pipeline-flow.puml`

Detailed flow of webhook processing:
- Step-by-step pipeline flow
- Data transformations
- External service interactions
- Component responsibilities

## Viewing the Diagrams

### VS Code Extension

1. **Install PlantUML Extension**:
   - Open Extensions (`Ctrl+Shift+X`)
   - Search for "PlantUML" (by jebbs)
   - Install and restart VS Code

2. **Open Preview**:
   - Open a `.puml` file
   - Press **`Alt+D`** (or `Option+D` on Mac) to preview
   - Or use Command Palette (`Ctrl+Shift+P`): "PlantUML: Preview Current Diagram"

3. **Check local rendering version**:
   - If you see errors like `%chr` unknown or "requires version >= 1.2021.6", refer to [puml-setup.md](puml-setup.md) for the configuration fix.

### Online Viewer

- Visit: https://www.plantuml.com/plantuml/uml/
- Copy the `.puml` file content
- Paste into the editor
- View rendered diagram

### Generate Images

```bash
# Using PlantUML JAR
java -jar plantuml.jar docs/plantuml/architecture-context.puml

# Using Docker
docker run --rm -v $(pwd)/docs/plantuml:/work plantuml/plantuml /work/architecture-context.puml

# Render all diagrams
for puml in docs/plantuml/*.puml; do
    java -jar plantuml.jar "$puml"
done
```

## Diagram Standards

These diagrams follow a C4-style architecture documentation approach:

- **Context** (Level 1): Shows the system in context with users and external systems
- **Container** (Level 2): Shows the high-level technical building blocks
- **Pipeline Flow**: Shows detailed step-by-step processing flow

## References

- [PlantUML](https://plantuml.com/)
- [C4 Model](https://c4model.com/) (inspiration for diagram structure)
