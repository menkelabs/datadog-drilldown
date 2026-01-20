#!/bin/bash
#
# Generate PlantUML diagrams as PNG images
#
# This script generates PNG images from PlantUML source files and saves them
# to docs/img/ with filenames based on the source puml filenames.
#
# Usage:
#   ./generate-diagrams.sh                    # Generate all diagrams
#   ./generate-diagrams.sh <pattern>          # Generate matching files only
#
# Examples:
#   ./generate-diagrams.sh
#   ./generate-diagrams.sh c4-context
#   ./generate-diagrams.sh sequence
#

set -e

# Script directory (where this script is located)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Project root (two levels up from scripts)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Paths
PLANTUML_JAR="$PROJECT_ROOT/tools/plantuml/plantuml.jar"
OUTPUT_DIR="$PROJECT_ROOT/docs/img"
TEMP_DIR="/tmp/plantuml-gen-$$"

# Source directories
ARCHITECTURE_DIR="$PROJECT_ROOT/embabel-dice-rca/docs/architecture"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if PlantUML jar exists
check_plantuml() {
    if [ ! -f "$PLANTUML_JAR" ]; then
        log_error "PlantUML jar not found at: $PLANTUML_JAR"
        log_error "Please ensure PlantUML is installed at tools/plantuml/plantuml.jar"
        exit 1
    fi
    
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi
}

# Create output directory if it doesn't exist
setup_directories() {
    mkdir -p "$OUTPUT_DIR"
    mkdir -p "$TEMP_DIR"
}

# Cleanup temp directory
cleanup() {
    rm -rf "$TEMP_DIR"
}

# Set trap to cleanup on exit
trap cleanup EXIT INT TERM

# Generate image from a single puml file
generate_image() {
    local puml_file="$1"
    local pattern="${2:-}"
    
    # Skip if pattern is provided and file doesn't match
    if [ -n "$pattern" ] && ! echo "$puml_file" | grep -q "$pattern"; then
        return 0
    fi
    
    # Get base filename without extension
    local base=$(basename "$puml_file" .puml)
    local output_file="$OUTPUT_DIR/${base}.png"
    
    log_info "Generating: $(basename "$puml_file")"
    
    # Get timestamp before generation
    local before_timestamp=$(date +%s)
    
    # Generate to temp directory first (redirect stderr to capture errors but allow success)
    java -jar "$PLANTUML_JAR" -tpng "$puml_file" -o "$TEMP_DIR" 2>&1 | grep -v "^(" | grep -v "^$" > /dev/null || true
    
    # Find the generated PNG file (PlantUML names files based on title, not filename)
    # Look for files modified after our timestamp
    local generated_png=$(find "$TEMP_DIR" -name "*.png" -type f 2>/dev/null | head -1)
    
    # Alternative: check if any PNG was created in temp dir
    if [ -z "$generated_png" ]; then
        # Check for recently created PNG files
        generated_png=$(find "$TEMP_DIR" -name "*.png" -type f -newermt "@$before_timestamp" 2>/dev/null | head -1)
    fi
    
    if [ -n "$generated_png" ] && [ -f "$generated_png" ]; then
        # Move and rename to match source filename
        mv "$generated_png" "$output_file"
        local size=$(ls -lh "$output_file" | awk '{print $5}')
        log_info "  ✓ Created: $(basename "$output_file") ($size)"
        return 0
    else
        log_warn "  ✗ No PNG file generated from: $(basename "$puml_file")"
        return 1
    fi
}

# Generate all diagrams
generate_all() {
    local pattern="${1:-}"
    local count=0
    local failed=0
    
    log_info "=== Generating PlantUML Diagrams ==="
    log_info "Output directory: $OUTPUT_DIR"
    log_info "Pattern filter: ${pattern:-none (all files)}"
    echo ""
    
    # Generate from architecture directory
    if [ -d "$ARCHITECTURE_DIR" ]; then
        log_info "Processing files from: $ARCHITECTURE_DIR"
        
        while IFS= read -r -d '' puml_file; do
            if generate_image "$puml_file" "$pattern"; then
                ((count++))
                sleep 0.1  # Small delay to ensure file timestamps are different
            else
                ((failed++))
            fi
        done < <(find "$ARCHITECTURE_DIR" -maxdepth 1 -name "*.puml" -type f -print0)
    else
        log_warn "Architecture directory not found: $ARCHITECTURE_DIR"
    fi
    
    echo ""
    log_info "=== Generation Complete ==="
    log_info "Successfully generated: $count files"
    if [ $failed -gt 0 ]; then
        log_warn "Failed: $failed files"
    fi
    
    # List generated files
    if [ $count -gt 0 ]; then
        echo ""
        log_info "Generated images:"
        find "$OUTPUT_DIR" -name "*.png" -type f -newer "$TEMP_DIR" 2>/dev/null | while read -r img; do
            local size=$(ls -lh "$img" | awk '{print $5}')
            echo "  - $(basename "$img") ($size)"
        done
    fi
}

# Main execution
main() {
    local pattern="${1:-}"
    
    check_plantuml
    setup_directories
    generate_all "$pattern"
}

# Run main function with all arguments
main "$@"
