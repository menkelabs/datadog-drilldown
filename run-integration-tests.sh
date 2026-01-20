#!/bin/bash
#
# Script to run integration tests with dice-server
# Usage: ./run-integration-tests.sh [test-pattern]
#
# Examples:
#   ./run-integration-tests.sh                    # Run all tests
#   ./run-integration-tests.sh SystemIntegration  # Run specific test
#
# Environment:
#   Expects a .env file in the project root with:
#     OPENAI_API_KEY=your-key-here
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DICE_SERVER_DIR="$SCRIPT_DIR/dice-server"
RCA_DIR="$SCRIPT_DIR/embabel-dice-rca"
DICE_SERVER_URL="http://localhost:8080"
DICE_SERVER_PID=""
TEST_PATTERN="${1:-}"
ENV_FILE="$SCRIPT_DIR/.env"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

# Load environment variables from .env file
load_env() {
    if [ -f "$ENV_FILE" ]; then
        log_info "Loading environment from $ENV_FILE"
        set -a  # automatically export all variables
        source "$ENV_FILE"
        set +a
    else
        log_warn "No .env file found at $ENV_FILE"
        log_warn "Create one with: echo 'OPENAI_API_KEY=your-key' > .env"
    fi
    
    # Verify required env vars
    if [ -z "$OPENAI_API_KEY" ]; then
        log_error "OPENAI_API_KEY is not set. The dice-server requires it."
        log_error "Set it in .env or export it: export OPENAI_API_KEY=your-key"
        exit 1
    fi
    
    # Explicitly export to ensure it's available to child processes
    export OPENAI_API_KEY
    log_info "OPENAI_API_KEY is configured (length: ${#OPENAI_API_KEY})"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

cleanup() {
    if [ -n "$DICE_SERVER_PID" ]; then
        log_info "Stopping dice-server (PID: $DICE_SERVER_PID)..."
        kill $DICE_SERVER_PID 2>/dev/null || true
        wait $DICE_SERVER_PID 2>/dev/null || true
        log_info "dice-server stopped."
    fi
}

# Set trap to cleanup on exit
trap cleanup EXIT INT TERM

wait_for_server() {
    local max_attempts=60
    local attempt=1
    
    log_info "Waiting for dice-server to be ready at $DICE_SERVER_URL..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s "$DICE_SERVER_URL/actuator/health" > /dev/null 2>&1; then
            log_info "dice-server is ready!"
            return 0
        fi
        
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    echo ""
    log_error "dice-server failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Check if dice-server is already running
check_existing_server() {
    if curl -s "$DICE_SERVER_URL/actuator/health" > /dev/null 2>&1; then
        log_info "dice-server is already running at $DICE_SERVER_URL"
        return 0
    fi
    return 1
}

# Build dice-server if needed
build_dice_server() {
    log_info "Building dice-server..."
    cd "$DICE_SERVER_DIR"
    mvn package -DskipTests -q
    log_info "dice-server built successfully."
}

# Start dice-server in background
start_dice_server() {
    log_info "Starting dice-server with OPENAI_API_KEY..."
    cd "$DICE_SERVER_DIR"
    
    # Verify API key is set and export it explicitly
    if [ -z "$OPENAI_API_KEY" ]; then
        log_error "OPENAI_API_KEY is not set! Cannot start dice-server."
        exit 1
    fi
    export OPENAI_API_KEY
    log_info "OPENAI_API_KEY is set (length: ${#OPENAI_API_KEY}, first 4 chars: ${OPENAI_API_KEY:0:4}...)"
    
    # Explicitly pass OPENAI_API_KEY to the Maven process and ensure it's in the environment
    # The environment will be inherited by the spawned JVM
    env OPENAI_API_KEY="$OPENAI_API_KEY" mvn spring-boot:run -q > /tmp/dice-server.log 2>&1 &
    DICE_SERVER_PID=$!
    log_info "dice-server starting with PID: $DICE_SERVER_PID"
    log_info "dice-server logs: tail -f /tmp/dice-server.log"
}

# Run standalone E2E test
run_e2e_test() {
    log_info "Running standalone E2E integration test..."
    cd "$RCA_DIR"
    
    # Export environment variables
    export DICE_SERVER_URL="$DICE_SERVER_URL"
    log_info "DICE_SERVER_URL=$DICE_SERVER_URL"
    
    # Run standalone E2E test via JUnit wrapper
    log_info "Executing StandaloneE2ETestRunner..."
    mvn test -Dtest="StandaloneE2ETestRunner" -q
}

# Run regular unit/integration tests
run_regular_tests() {
    log_info "Running regular integration tests..."
    cd "$RCA_DIR"
    
    if [ -n "$TEST_PATTERN" ]; then
        log_info "Test pattern: $TEST_PATTERN"
        mvn test -Dtest="*${TEST_PATTERN}*"
    else
        mvn test
    fi
}

# Main execution
main() {
    log_info "=== E2E Integration Test Runner ==="
    
    # Load environment variables
    load_env
    
    # Check if server is already running
    if check_existing_server; then
        DICE_SERVER_PID=""  # Don't kill existing server
        log_info "Using existing dice-server instance"
    else
        build_dice_server
        start_dice_server
        
        if ! wait_for_server; then
            log_error "Failed to start dice-server"
            exit 1
        fi
    fi
    
    # Run standalone E2E test
    if run_e2e_test; then
        log_info "=== E2E Integration Test PASSED ==="
    else
        log_error "=== E2E Integration Test FAILED ==="
        exit 1
    fi
}

main "$@"
