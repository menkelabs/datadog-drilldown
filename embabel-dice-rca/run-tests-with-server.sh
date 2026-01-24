#!/bin/bash
#
# Script to start dice-server and run tests
# Usage: ./run-tests-with-server.sh [test-pattern] [--verbose]
#
# Examples:
#   ./run-tests-with-server.sh                    # Run all tests (excludes SystemIntegrationTest)
#   ./run-tests-with-server.sh DiceRcaIntegration # Run specific test pattern
#   ./run-tests-with-server.sh "" --verbose       # Run all tests with verbose output
#
# Environment:
#   Expects a .env file (in project root or current directory) with:
#     OPENAI_API_KEY=your-key-here
#     DICE_SERVER_DIR=/path/to/dice-server (optional, defaults to ../dice-server)
#
# Viewing Logs:
#   The dice-server logs are written to /tmp/dice-server.log
#   To view logs in real-time during test execution, open another terminal and run:
#     tail -f /tmp/dice-server.log
#   The script will also show log excerpts before and after test execution.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RCA_DIR="$SCRIPT_DIR"

# Look for .env file in project root or current directory
ENV_FILE=""
if [ -f "$PROJECT_ROOT/.env" ]; then
    ENV_FILE="$PROJECT_ROOT/.env"
elif [ -f "$SCRIPT_DIR/.env" ]; then
    ENV_FILE="$SCRIPT_DIR/.env"
fi

# Default values
DICE_SERVER_DIR="${DICE_SERVER_DIR:-$PROJECT_ROOT/dice-server}"
DICE_SERVER_URL="http://localhost:8080"
DICE_SERVER_PID=""
TEST_PATTERN="${1:-}"
VERBOSE=false

# Parse arguments
if [[ "$*" == *"--verbose"* ]] || [[ "$*" == *"-v"* ]]; then
    VERBOSE=true
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

# Load environment variables from .env file
load_env() {
    if [ -n "$ENV_FILE" ] && [ -f "$ENV_FILE" ]; then
        log_info "Loading environment from $ENV_FILE"
        set -a  # automatically export all variables
        source "$ENV_FILE"
        set +a
        
        # Override DICE_SERVER_DIR if set in .env (relative paths resolved from project root)
        if [ -n "$DICE_SERVER_DIR" ] && [ "${DICE_SERVER_DIR#/}" = "$DICE_SERVER_DIR" ]; then
            # Relative path - resolve from project root
            DICE_SERVER_DIR="$PROJECT_ROOT/$DICE_SERVER_DIR"
        fi
    else
        log_warn "No .env file found at $PROJECT_ROOT/.env or $SCRIPT_DIR/.env"
        log_warn "Using defaults and environment variables"
    fi
    
    # Verify required env vars
    if [ -z "$OPENAI_API_KEY" ]; then
        log_error "OPENAI_API_KEY is not set. The dice-server requires it."
        log_error "Set it in .env or export it: export OPENAI_API_KEY=your-key"
        exit 1
    fi
    
    # Verify DICE_SERVER_DIR exists
    if [ ! -d "$DICE_SERVER_DIR" ]; then
        log_error "DICE_SERVER_DIR does not exist: $DICE_SERVER_DIR"
        log_error "Set DICE_SERVER_DIR in .env or ensure dice-server directory exists"
        exit 1
    fi
    
    # Explicitly export to ensure it's available to child processes
    export OPENAI_API_KEY
    export DICE_SERVER_URL
    log_info "OPENAI_API_KEY is configured (length: ${#OPENAI_API_KEY})"
    log_info "DICE_SERVER_DIR: $DICE_SERVER_DIR"
    log_info "DICE_SERVER_URL: $DICE_SERVER_URL"
}

cleanup() {
    if [ -n "$DICE_SERVER_PID" ]; then
        log_info "Stopping dice-server (PID: $DICE_SERVER_PID)..."
        
        # Find all related processes (Maven and Java)
        local all_pids="$DICE_SERVER_PID"
        
        # Find Maven process if it's different
        local maven_pids=$(ps aux | grep "mvn spring-boot:run" | grep -v grep | awk '{print $2}' || true)
        if [ -n "$maven_pids" ]; then
            for pid in $maven_pids; do
                if [[ ! " $all_pids " =~ " $pid " ]]; then
                    all_pids="$all_pids $pid"
                fi
            done
        fi
        
        # Find Java processes related to dice-server
        local java_pids=$(ps aux | grep -i "dice-server\|DiceServerApplication" | grep -v grep | grep java | awk '{print $2}' || true)
        if [ -n "$java_pids" ]; then
            for pid in $java_pids; do
                if [[ ! " $all_pids " =~ " $pid " ]]; then
                    all_pids="$all_pids $pid"
                fi
            done
        fi
        
        # Kill all related processes
        for pid in $all_pids; do
            if kill -0 "$pid" 2>/dev/null; then
                log_info "Stopping process $pid..."
                kill "$pid" 2>/dev/null || true
            fi
        done
        
        # Wait for processes to stop
        sleep 2
        
        # Force kill if still running
        for pid in $all_pids; do
            if kill -0 "$pid" 2>/dev/null; then
                log_warn "Process $pid still running, force killing..."
                kill -9 "$pid" 2>/dev/null || true
            fi
        done
        
        log_info "dice-server stopped."
        
        # Show final log entries
        if [ -f /tmp/dice-server.log ]; then
            echo ""
            log_info "Final dice-server log entries:"
            tail -n 10 /tmp/dice-server.log
        fi
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
    log_error "Check server logs: tail -f /tmp/dice-server.log"
    return 1
}

# Check if dice-server is already running
check_existing_server() {
    if curl -s "$DICE_SERVER_URL/actuator/health" > /dev/null 2>&1; then
        return 0
    fi
    return 1
}

# Kill any existing dice-server processes
kill_existing_server() {
    log_info "Checking for existing dice-server processes..."
    
    # Extract port from DICE_SERVER_URL (default to 8080)
    local port="8080"
    if [[ "$DICE_SERVER_URL" =~ :([0-9]+) ]]; then
        port="${BASH_REMATCH[1]}"
    fi
    
    # Find processes using the port
    local pids_on_port=""
    if command -v lsof >/dev/null 2>&1; then
        pids_on_port=$(lsof -ti:$port 2>/dev/null || true)
    elif command -v netstat >/dev/null 2>&1; then
        pids_on_port=$(netstat -tlnp 2>/dev/null | grep ":$port " | awk '{print $7}' | cut -d'/' -f1 | grep -v "^$" || true)
    elif command -v ss >/dev/null 2>&1; then
        pids_on_port=$(ss -tlnp 2>/dev/null | grep ":$port " | grep -oP 'pid=\K[0-9]+' | sort -u || true)
    fi
    
    # Also find Java processes that might be dice-server (Spring Boot apps)
    local java_pids=""
    if [ -n "$DICE_SERVER_DIR" ]; then
        local server_jar=$(find "$DICE_SERVER_DIR/target" -name "dice-server*.jar" -type f 2>/dev/null | head -1)
        if [ -n "$server_jar" ]; then
            java_pids=$(ps aux | grep -i "dice-server" | grep -v grep | awk '{print $2}' || true)
        fi
    fi
    
    # Combine all PIDs to kill (avoid duplicates)
    local all_pids=""
    local seen_pids=""
    
    # Add PIDs from port
    if [ -n "$pids_on_port" ]; then
        for pid in $pids_on_port; do
            if [[ ! " $seen_pids " =~ " $pid " ]]; then
                all_pids="$all_pids $pid"
                seen_pids="$seen_pids $pid"
            fi
        done
    fi
    
    # Add Java PIDs (avoid duplicates)
    if [ -n "$java_pids" ]; then
        for pid in $java_pids; do
            if [[ ! " $seen_pids " =~ " $pid " ]]; then
                all_pids="$all_pids $pid"
                seen_pids="$seen_pids $pid"
            fi
        done
    fi
    
    # Trim leading space
    all_pids=$(echo "$all_pids" | sed 's/^ *//')
    
    if [ -z "$all_pids" ]; then
        log_info "No existing dice-server processes found"
        return 0
    fi
    
    log_warn "Found existing dice-server processes on port $port, killing them..."
    for pid in $all_pids; do
        # Verify it's a valid PID
        if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
            continue
        fi
        
        if kill -0 "$pid" 2>/dev/null; then
            log_info "Killing process $pid (using port $port)..."
            # Try graceful shutdown first (SIGTERM)
            kill "$pid" 2>/dev/null || true
            sleep 2
            # Force kill if still running (SIGKILL)
            if kill -0 "$pid" 2>/dev/null; then
                log_warn "Process $pid still running, force killing with SIGKILL..."
                kill -9 "$pid" 2>/dev/null || true
                sleep 1
            else
                log_info "Process $pid terminated gracefully"
            fi
        fi
    done
    
    # Wait a moment for processes to die
    sleep 2
    
    # Verify they're gone
    if check_existing_server; then
        log_warn "dice-server still appears to be running, waiting a bit longer..."
        sleep 3
        if check_existing_server; then
            log_error "Failed to kill existing dice-server. Please kill it manually."
            return 1
        fi
    fi
    
    log_info "Existing dice-server processes killed successfully"
    return 0
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
    
    # Clear previous log
    > /tmp/dice-server.log
    
    # Start server in background
    # Use nohup and disown to ensure it keeps running even if parent shell exits
    log_info "Starting Maven Spring Boot process..."
    cd "$DICE_SERVER_DIR"
    nohup env OPENAI_API_KEY="$OPENAI_API_KEY" mvn spring-boot:run -q > /tmp/dice-server.log 2>&1 &
    local maven_pid=$!
    log_info "Maven process started with PID: $maven_pid"
    
    # Wait a bit for Spring Boot to start and find the actual Java process
    sleep 5
    
    # Find the actual Java process running Spring Boot (child of Maven)
    local java_pid=""
    local max_wait=30
    local wait_count=0
    
    while [ -z "$java_pid" ] && [ $wait_count -lt $max_wait ]; do
        # Look for Java process with dice-server in the command line
        java_pid=$(ps aux | grep -i "dice-server\|DiceServerApplication" | grep -v grep | grep java | awk '{print $2}' | head -1)
        if [ -n "$java_pid" ]; then
            break
        fi
        sleep 1
        wait_count=$((wait_count + 1))
    done
    
    if [ -n "$java_pid" ]; then
        DICE_SERVER_PID=$java_pid
        log_info "Found Spring Boot Java process with PID: $DICE_SERVER_PID"
    else
        # Fallback to Maven PID if we can't find Java process
        DICE_SERVER_PID=$maven_pid
        log_warn "Could not find Java process, using Maven PID: $DICE_SERVER_PID"
    fi
    
    log_info "dice-server log file: /tmp/dice-server.log"
    log_info "To tail logs: tail -f /tmp/dice-server.log"
    
    # Show initial log output
    if [ -f /tmp/dice-server.log ]; then
        log_info "Initial dice-server output:"
        cat /tmp/dice-server.log 2>/dev/null | tail -15 || true
    fi
    
    # Verify the process is still running
    if ! kill -0 "$DICE_SERVER_PID" 2>/dev/null; then
        log_error "dice-server process died immediately after starting!"
        log_error "Check logs: tail -50 /tmp/dice-server.log"
        return 1
    fi
}

# Show recent dice-server logs
show_server_logs() {
    local lines="${1:-20}"
    if [ -f /tmp/dice-server.log ]; then
        echo ""
        echo "=========================================="
        echo "  DICE-SERVER LOGS (last $lines lines)"
        echo "=========================================="
        tail -n "$lines" /tmp/dice-server.log
        echo "=========================================="
        echo ""
    fi
}

# Show live log updates (non-blocking, shows recent lines)
show_live_log_updates() {
    if [ -f /tmp/dice-server.log ] && [ -n "$DICE_SERVER_PID" ]; then
        # Show what's new in the log since last check
        local last_size="${1:-0}"
        local current_size=$(stat -f%z /tmp/dice-server.log 2>/dev/null || stat -c%s /tmp/dice-server.log 2>/dev/null || echo 0)
        if [ "$current_size" -gt "$last_size" ]; then
            tail -c +$((last_size + 1)) /tmp/dice-server.log 2>/dev/null | head -20
        fi
        echo "$current_size"
    else
        echo "0"
    fi
}

# Stop tailing log (no-op now, kept for compatibility)
stop_tail_log() {
    true
}

# Run tests
run_tests() {
    log_info "Running tests..."
    cd "$RCA_DIR"
    
    # Export environment variables for tests
    export DICE_SERVER_URL="$DICE_SERVER_URL"
    log_info "DICE_SERVER_URL=$DICE_SERVER_URL"
    
    # Show recent server logs before tests
    show_server_logs 10
    
    echo ""
    echo "=========================================="
    echo "  STARTING TEST EXECUTION"
    echo "=========================================="
    echo ""
    log_info "To view dice-server logs in real-time, run in another terminal:"
    log_info "  tail -f /tmp/dice-server.log"
    echo ""
    
    local test_result=0
    local mvn_args=""
    
    if [ "$VERBOSE" = true ]; then
        mvn_args="-X"  # Debug mode for verbose output
        log_info "Running in verbose mode..."
    fi
    
    # Ensure output is visible (Maven shows test results by default)
    # Don't use -q flag so we can see all test output
    
    if [ -n "$TEST_PATTERN" ]; then
        log_info "Test pattern: $TEST_PATTERN"
        echo ""
        mvn test $mvn_args -Dtest="*${TEST_PATTERN}*" || test_result=$?
    else
        log_info "Running all tests (excluding SystemIntegrationTest)..."
        echo ""
        # Exclude SystemIntegrationTest - it hangs after Spring Boot context load
        mvn test $mvn_args -Dtest='!*SystemIntegrationTest*' || test_result=$?
    fi
    
    echo ""
    echo "=========================================="
    echo "  TEST EXECUTION COMPLETE"
    echo "=========================================="
    echo ""
    
    # Show server logs after tests (especially if tests failed)
    if [ $test_result -ne 0 ]; then
        log_warn "Tests failed. Showing recent dice-server logs:"
        show_server_logs 50
        echo ""
        log_info "View full logs: tail -f /tmp/dice-server.log"
    else
        log_info "Tests passed. Recent dice-server logs:"
        show_server_logs 20
    fi
    
    return $test_result
}

# Main execution
main() {
    log_info "=== Dice Server Test Runner ==="
    
    # Load environment variables
    load_env
    
    # Kill any existing dice-server processes
    if ! kill_existing_server; then
        log_error "Failed to kill existing dice-server. Exiting."
        exit 1
    fi
    
    # Build and start dice-server
    build_dice_server
    start_dice_server
    
    if ! wait_for_server; then
        log_error "Failed to start dice-server"
        exit 1
    fi
    
    # Run tests
    echo ""
    log_info "=========================================="
    log_info "Starting test execution..."
    log_info "=========================================="
    echo ""
    
    if run_tests; then
        echo ""
        log_info "=========================================="
        log_info "=== Tests PASSED ==="
        log_info "=========================================="
        echo ""
    else
        echo ""
        log_error "=========================================="
        log_error "=== Tests FAILED ==="
        log_error "=========================================="
        echo ""
        exit 1
    fi
}

main "$@"
