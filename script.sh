#!/bin/bash

echo "=== Virtual Threads Performance Demo ==="
echo ""

# Test CUSTOM
echo "Testing CUSTOM (16 threads)..."
curl -s localhost:8080/mode/custom > /dev/null
curl -s localhost:8080/reset > /dev/null
hey -z 20s -c 2000 "http://localhost:8080/sleep?ms=400" 2>&1 | grep -E "Requests/sec|Total:|requests"
echo "Metrics:"
curl -s localhost:8080/metrics
echo ""

# Test PLATFORM
echo "Testing PLATFORM..."
curl -s localhost:8080/mode/platform > /dev/null
curl -s localhost:8080/reset > /dev/null
hey -z 20s -c 2000  "http://localhost:8080/sleep?ms=400" 2>&1 | grep -E "Requests/sec|Total:|requests"
echo "Metrics:"
curl -s localhost:8080/metrics
echo ""

# Test VIRTUAL
echo "Testing VIRTUAL..."
curl -s localhost:8080/mode/virtual > /dev/null
curl -s localhost:8080/reset > /dev/null
hey -z 20s -c 2000  "http://localhost:8080/sleep?ms=400" 2>&1 | grep -E "Requests/sec|Total:|requests"
echo "Metrics:"
curl -s localhost:8080/metrics
echo ""

echo "=== Summary ==="
echo "Custom Pool: Bottlenecked by 16 threads"
echo "Platform Threads: Limited by Tomcat worker pool (blocking)"
echo "Virtual Threads: Scales to high concurrency for blocking I/O"


