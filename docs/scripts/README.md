# Documentation Scripts

This directory contains utility scripts for generating and maintaining documentation.

## generate-diagrams.sh

Generates PNG images from PlantUML source files located in `embabel-dice-rca/docs/architecture/`.

### Features

- Automatically discovers all `.puml` files in the architecture directory
- Generates PNG images with filenames matching the source files
- Handles PlantUML's title-based naming (generates based on diagram title, then renames to match source filename)
- Provides colored output with success/failure indicators
- Supports pattern filtering for selective generation
- Automatically cleans up temporary files

### Usage

```bash
# Generate all diagrams
./docs/scripts/generate-diagrams.sh

# Generate only diagrams matching a pattern
./docs/scripts/generate-diagrams.sh c4-context
./docs/scripts/generate-diagrams.sh sequence
./docs/scripts/generate-diagrams.sh agent
```

### Requirements

- Java installed and in PATH
- PlantUML jar located at `tools/plantuml/plantuml.jar`
- Source files in `embabel-dice-rca/docs/architecture/*.puml`

### Output

Generated images are saved to `docs/img/` with filenames matching the source puml files:

- `c4-context.puml` → `docs/img/c4-context.png`
- `agent-workflow.puml` → `docs/img/agent-workflow.png`
- etc.

### Examples

```bash
# Generate all diagrams
cd /home/ubuntu/github/jmjava/datadog-drilldown
./docs/scripts/generate-diagrams.sh

# Generate only C4 context diagrams
./docs/scripts/generate-diagrams.sh c4-context

# Generate only sequence diagrams
./docs/scripts/generate-diagrams.sh sequence
```

### How It Works

1. **Discovery**: Scans `embabel-dice-rca/docs/architecture/` for `.puml` files
2. **Generation**: For each file, runs PlantUML to generate PNG to a temporary directory
3. **Renaming**: PlantUML names files based on the diagram title, but the script renames them to match the source filename
4. **Output**: Moves renamed files to `docs/img/` directory
5. **Cleanup**: Removes temporary files on exit

### Troubleshooting

**Error: PlantUML jar not found**
- Ensure `tools/plantuml/plantuml.jar` exists
- Download from https://plantuml.com/download if missing

**Error: Java not found**
- Install Java (JDK 11+ recommended)
- Ensure `java` is in your PATH

**No images generated**
- Check that source `.puml` files exist in `embabel-dice-rca/docs/architecture/`
- Verify PlantUML syntax is correct (try generating one file manually)
- Check script output for specific error messages

**Generated images have wrong names**
- This is normal - PlantUML uses diagram titles for filenames
- The script automatically renames them to match source filenames
- If renaming fails, check that the temp directory is writable
