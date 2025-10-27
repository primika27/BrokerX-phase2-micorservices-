#!/bin/bash

# BrokerX Load Testing Script
# Usage: ./run-load-tests.sh [test-type] [environment]

set -e

# Configuration
TEST_TYPE=${1:-"load"}  # load, stress, spike
ENVIRONMENT=${2:-"local"}  # local, staging, prod
RESULTS_DIR="./results/$(date +%Y%m%d_%H%M%S)"

# Environment URLs
case $ENVIRONMENT in
  "local")
    BASE_URL="http://localhost"
    ;;
  "staging")
    BASE_URL="https://staging.brokerx.com"
    ;;
  "prod")
    BASE_URL="https://api.brokerx.com"
    ;;
  *)
    echo "Unknown environment: $ENVIRONMENT"
    exit 1
    ;;
esac

echo "🚀 Starting BrokerX Load Tests"
echo "Environment: $ENVIRONMENT ($BASE_URL)"
echo "Test Type: $TEST_TYPE"
echo "Results Directory: $RESULTS_DIR"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Health check before testing
echo "📊 Performing pre-test health check..."
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
  echo "❌ Health check failed. System may be down."
  exit 1
fi
echo "✅ System is healthy"

# Run the appropriate test
case $TEST_TYPE in
  "load")
    echo "🔄 Running load test..."
    k6 run --out json="$RESULTS_DIR/load-test-results.json" \
           --env BASE_URL="$BASE_URL" \
           ./brokerx-load-test.js
    ;;
  "stress")
    echo "⚡ Running stress test..."
    k6 run --out json="$RESULTS_DIR/stress-test-results.json" \
           --env BASE_URL="$BASE_URL" \
           ./stress-test.js
    ;;
  "spike")
    echo "🌊 Running spike test..."
    k6 run --out json="$RESULTS_DIR/spike-test-results.json" \
           --env BASE_URL="$BASE_URL" \
           ./spike-test.js
    ;;
  *)
    echo "Unknown test type: $TEST_TYPE"
    echo "Available types: load, stress, spike"
    exit 1
    ;;
esac

# Post-test health check
echo "🔍 Performing post-test health check..."
if ! curl -s "$BASE_URL/api/health" > /dev/null; then
  echo "⚠️  Warning: System may be degraded after testing"
else
  echo "✅ System is still healthy"
fi

echo "📈 Test completed. Results saved to: $RESULTS_DIR"
echo "🔗 View Grafana dashboard: http://localhost:3000"

# Generate summary report
if command -v jq &> /dev/null; then
  echo "📊 Generating summary report..."
  
  # Extract key metrics from JSON results
  RESULT_FILE="$RESULTS_DIR/${TEST_TYPE}-test-results.json"
  if [ -f "$RESULT_FILE" ]; then
    echo "=== Test Summary ===" > "$RESULTS_DIR/summary.txt"
    echo "Environment: $ENVIRONMENT" >> "$RESULTS_DIR/summary.txt"
    echo "Test Type: $TEST_TYPE" >> "$RESULTS_DIR/summary.txt"
    echo "Date: $(date)" >> "$RESULTS_DIR/summary.txt"
    echo "" >> "$RESULTS_DIR/summary.txt"
    
    # Calculate summary metrics
    jq -r '
      select(.type == "Point" and .metric == "http_req_duration") |
      .data.value
    ' "$RESULT_FILE" | \
    awk '
    BEGIN { sum = 0; count = 0; min = 999999; max = 0 }
    { 
      sum += $1; count++; 
      if ($1 < min) min = $1;
      if ($1 > max) max = $1;
    }
    END { 
      if (count > 0) {
        printf "Average Response Time: %.2f ms\n", sum/count;
        printf "Min Response Time: %.2f ms\n", min;
        printf "Max Response Time: %.2f ms\n", max;
      }
    }' >> "$RESULTS_DIR/summary.txt"
    
    echo "Summary report generated: $RESULTS_DIR/summary.txt"
  fi
else
  echo "📝 Install jq for detailed summary reports"
fi

echo "🎉 Load testing complete!"