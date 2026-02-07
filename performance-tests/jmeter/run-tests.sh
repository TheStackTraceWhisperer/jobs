#!/bin/bash
# JMeter Test Runner Script
# Usage: ./run-tests.sh [throughput|latency|stress|all] [options]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Default values
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
RESULTS_DIR="./results"
LOGS_DIR="./logs"
REPORTS_DIR="./reports"
NON_INTERACTIVE="${NON_INTERACTIVE:-false}"

# Detect if running in non-interactive mode (CI)
if [[ ! -t 0 ]] || [[ -n "${CI}" ]]; then
    NON_INTERACTIVE=true
fi

# Create directories if they don't exist
mkdir -p "$RESULTS_DIR" "$LOGS_DIR" "$REPORTS_DIR"

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if JMeter is installed
check_jmeter() {
    if ! command -v jmeter &> /dev/null; then
        print_error "JMeter is not installed or not in PATH"
        echo "Please install JMeter:"
        echo "  macOS: brew install jmeter"
        echo "  Linux: sudo apt-get install jmeter"
        echo "  Or download from: https://jmeter.apache.org/download_jmeter.cgi"
        exit 1
    fi
    print_info "JMeter found: $(jmeter --version | head -n1)"
}

# Function to check if application is running
check_application() {
    print_info "Checking if application is running at http://${HOST}:${PORT}"
    if curl -s -f --connect-timeout 5 --max-time 10 "http://${HOST}:${PORT}/actuator/health" > /dev/null; then
        print_info "Application is running and healthy"
    else
        print_warning "Application is not reachable at http://${HOST}:${PORT}"
        print_warning "Please start the application before running tests"
        
        if [[ "$NON_INTERACTIVE" == "true" ]]; then
            print_error "Running in non-interactive mode. Exiting."
            exit 1
        fi
        
        read -p "Continue anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# Function to run throughput test
run_throughput_test() {
    local threads="${1:-50}"
    local duration="${2:-300}"
    local timestamp=$(date +%Y%m%d-%H%M%S)
    
    print_info "Running Throughput Test (threads=$threads, duration=${duration}s)"
    
    jmeter -n -t throughput-test.jmx \
        -q jmeter.properties \
        -Jhost="$HOST" \
        -Jport="$PORT" \
        -Jthreads="$threads" \
        -Jduration="$duration" \
        -l "$RESULTS_DIR/throughput-${timestamp}.jtl" \
        -j "$LOGS_DIR/throughput-${timestamp}.log" \
        -e -o "$REPORTS_DIR/throughput-${timestamp}"
    
    print_info "Throughput test completed"
    print_info "Report available at: $REPORTS_DIR/throughput-${timestamp}/index.html"
}

# Function to run latency test
run_latency_test() {
    local threads="${1:-25}"
    local loops="${2:-1000}"
    local timestamp=$(date +%Y%m%d-%H%M%S)
    
    print_info "Running Latency Test (threads=$threads, loops=$loops)"
    
    jmeter -n -t latency-test.jmx \
        -q jmeter.properties \
        -Jhost="$HOST" \
        -Jport="$PORT" \
        -Jthreads="$threads" \
        -Jloops="$loops" \
        -l "$RESULTS_DIR/latency-${timestamp}.jtl" \
        -j "$LOGS_DIR/latency-${timestamp}.log" \
        -e -o "$REPORTS_DIR/latency-${timestamp}"
    
    print_info "Latency test completed"
    print_info "Report available at: $REPORTS_DIR/latency-${timestamp}/index.html"
}

# Function to run stress test
run_stress_test() {
    local baseline="${1:-50}"
    local peak="${2:-200}"
    local spike="${3:-500}"
    local timestamp=$(date +%Y%m%d-%H%M%S)
    
    print_info "Running Stress Test (baseline=$baseline, peak=$peak, spike=$spike)"
    
    jmeter -n -t stress-test.jmx \
        -q jmeter.properties \
        -Jhost="$HOST" \
        -Jport="$PORT" \
        -Jbaseline_threads="$baseline" \
        -Jpeak_threads="$peak" \
        -Jspike_threads="$spike" \
        -l "$RESULTS_DIR/stress-${timestamp}.jtl" \
        -j "$LOGS_DIR/stress-${timestamp}.log" \
        -e -o "$REPORTS_DIR/stress-${timestamp}"
    
    print_info "Stress test completed"
    print_info "Report available at: $REPORTS_DIR/stress-${timestamp}/index.html"
}

# Function to show usage
show_usage() {
    cat << EOF
Usage: $0 [test_type] [options]

Test Types:
    throughput      Run throughput test
    latency         Run latency test
    stress          Run stress test
    all             Run all tests sequentially

Options:
    -h, --host      Target host (default: localhost)
    -p, --port      Target port (default: 8080)
    --help          Show this help message

Environment Variables:
    HOST            Target host (default: localhost)
    PORT            Target port (default: 8080)

Examples:
    # Run throughput test with default settings
    $0 throughput

    # Run latency test against remote host
    HOST=prod-server PORT=8080 $0 latency

    # Run stress test with custom parameters
    $0 stress

    # Run all tests
    $0 all

For more details, see README.md
EOF
}

# Main script logic
main() {
    check_jmeter
    
    # Parse command line arguments
    TEST_TYPE="${1:-}"
    
    # Parse options
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--host)
                if [[ -z "${2:-}" ]] || [[ "$2" == -* ]]; then
                    print_error "Option --host requires a non-empty host value"
                    show_usage
                    exit 1
                fi
                HOST="$2"
                shift 2
                ;;
            -p|--port)
                if [[ -z "${2:-}" ]] || [[ "$2" == -* ]]; then
                    print_error "Option --port requires a non-empty port value"
                    show_usage
                    exit 1
                fi
                if ! [[ "$2" =~ ^[0-9]+$ ]]; then
                    print_error "Invalid port: $2. Port must be a numeric value."
                    show_usage
                    exit 1
                fi
                PORT="$2"
                shift 2
                ;;
            --help)
                show_usage
                exit 0
                ;;
            throughput|latency|stress|all)
                TEST_TYPE="$1"
                shift
                ;;
            *)
                print_error "Unknown option or argument: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    if [[ -z "$TEST_TYPE" ]]; then
        print_error "No test type specified"
        show_usage
        exit 1
    fi
    
    check_application
    
    print_info "Starting JMeter tests against http://${HOST}:${PORT}"
    print_info "Results directory: $RESULTS_DIR"
    print_info "Logs directory: $LOGS_DIR"
    print_info "Reports directory: $REPORTS_DIR"
    echo
    
    case "$TEST_TYPE" in
        throughput)
            run_throughput_test
            ;;
        latency)
            run_latency_test
            ;;
        stress)
            run_stress_test
            ;;
        all)
            print_info "Running all tests sequentially..."
            run_throughput_test
            sleep 30  # Cool down period
            run_latency_test
            sleep 30  # Cool down period
            run_stress_test
            print_info "All tests completed!"
            ;;
        *)
            print_error "Unknown test type: $TEST_TYPE"
            show_usage
            exit 1
            ;;
    esac
    
    print_info "Test execution completed successfully!"
}

# Run main function
main "$@"
