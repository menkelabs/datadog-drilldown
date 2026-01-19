# PlantUML Local Setup Guide

This document documents the resolution for persistent rendering errors in the Cursor/VS Code PlantUML extension when using modern C4-PlantUML features.

## Problem

When using standard C4-PlantUML includes (e.g., from `plantuml-stdlib`), the following errors may occur during local rendering:

1.  **Unknown built-in function `%chr`**: A syntax error that halts rendering.
2.  **"dynamic undefined legend colors"**: A log warning stating `requires PlantUML version >= 1.2021.6`.

## Root Cause

The **PlantUML extension (jebbs.plantuml)** often bundles an outdated version of the `plantuml.jar` (e.g., v1.2021.00). Modern C4-PlantUML libraries utilize newer features like `%chr()` and dynamic contrast calculations that require **v1.2021.6 or higher**.

## Solution: Explicit Local JAR Configuration

To fix this properly without modifying your diagram code, you must point the extension to a modern, standalone version of the PlantUML JAR.

### 1. Download Modern PlantUML JAR
Download the latest `plantuml.jar` from the [official GitHub releases](https://github.com/plantuml/plantuml/releases).

In this project, we have placed it in:
`tools/plantuml/plantuml.jar`

### 2. Configure Workspace Settings
Update (or create) `.vscode/settings.json` at the project root to force the extension to use this specific JAR for local rendering:

```json
{
    "plantuml.render": "Local",
    "plantuml.exportFormat": "png",
    "plantuml.jar": "tools/plantuml/plantuml.jar"
}
```

## Benefits of this Setup

- **Offline Rendering**: Diagrams render locally without needing a connection to a PlantUML server.
- **Full Feature Support**: Supports the most modern C4-PlantUML features, including automatic legends, dynamic coloring, and the latest layout macros.
- **Consistency**: Ensures every developer on the project sees the exact same diagram regardless of their global IDE version.

## Verification
To verify the version currently used by your IDE, run this command in a terminal:
```bash
java -Djava.awt.headless=true -jar tools/plantuml/plantuml.jar -version
```
Expected: `PlantUML version 1.2026.1` (or higher).
