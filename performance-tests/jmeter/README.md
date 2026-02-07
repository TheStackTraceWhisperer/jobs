# JMeter Performance Tests

This directory contains comprehensive JMeter test plans for the Job Platform, designed to measure throughput, latency, and stress test the system under various load conditions.

## Test Plans Overview

### 1. Throughput Test (`throughput-test.jmx`)
**Purpose:** Measures the system's ability to handle sustained load and job processing throughput.

**Key Features:**
- Sustained load generation over configurable duration (default: 5 minutes)
- Concurrent job enqueueing simulation
- Real-time metrics collection from Prometheus endpoint
- Extracts and monitors:
  - Queue depth
  - Active workers
  - Jobs enqueued count
- Results exported to CSV for analysis

**Default Configuration:**
- Threads: 50 concurrent users
- Duration: 300 seconds (5 minutes)
- Ramp-up: 30 seconds

### 2. Latency Test (`latency-test.jmx`)
**Purpose:** Measures API response times and job processing latency with detailed percentile analysis.

**Key Features:**
- Multiple endpoint testing (health, metrics, prometheus)
- Response time measurement and analysis
- P95 and P99 latency extraction from job metrics
- Gaussian random think time for realistic user behavior
- Detailed latency breakdown:
  - API endpoint response times
  - Job execution times (from metrics)
  - Queue wait times (from metrics)

**Default Configuration:**
- Threads: 25 concurrent users
- Loops: 1000 iterations per thread
- Ramp-up: 10 seconds

### 3. Stress Test (`stress-test.jmx`)
**Purpose:** Tests system behavior under extreme load with progressive load increase and spike scenarios.

**Key Features:**
- **Phase 1 - Baseline Load (Warm-up):** 50 threads, 2 minutes
- **Phase 2 - Progressive Load Increase:** Ramps from 50 to 200 threads over 2 minutes, runs for 5 minutes
- **Phase 3 - Spike Test (Extreme Load):** Sudden spike to 500 threads for 1 minute
- **Phase 4 - Recovery Period:** Returns to 50 threads for 2 minutes to verify system recovery
- Continuous system health monitoring thread tracks:
  - Worker saturation
  - Queue depth
  - Error rates
- Allows 503 (Service Unavailable) and error responses during extreme load
- Validates system recovery after stress

**Default Configuration:**
- Baseline: 50 threads
- Peak: 200 threads
- Spike: 500 threads
- Total duration: ~10 minutes

## Prerequisites

### 1. Install Apache JMeter

**macOS (Homebrew):**
```bash
brew install jmeter
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get update
sudo apt-get install jmeter
```

**Windows / Manual Installation:**
1. Download from https://jmeter.apache.org/download_jmeter.cgi
2. Extract to desired location
3. Add `bin/` directory to PATH

**Verify Installation:**
```bash
jmeter --version
```

### 2. Start the Job Platform

Ensure the application is running before executing tests:

```bash
# Start database
cd /path/to/job-platform
docker-compose up -d

# Build and start application
mvn clean install
cd reference-app
mvn spring-boot:run -Dspring-boot.run.profiles=api,worker
```

Verify the application is running:
```bash
curl http://localhost:8080/actuator/health
```

## Running Tests

### GUI Mode (Development & Debugging)

**Throughput Test:**
```bash
jmeter -t throughput-test.jmx
```

**Latency Test:**
```bash
jmeter -t latency-test.jmx
```

**Stress Test:**
```bash
jmeter -t stress-test.jmx
```

### CLI Mode (Production & CI/CD)

**Throughput Test:**
```bash
jmeter -n -t throughput-test.jmx \
  -Jhost=localhost \
  -Jport=8080 \
  -Jthreads=50 \
  -Jduration=300 \
  -l results/throughput-results.jtl \
  -j logs/throughput-test.log \
  -e -o reports/throughput-report
```

**Latency Test:**
```bash
jmeter -n -t latency-test.jmx \
  -Jhost=localhost \
  -Jport=8080 \
  -Jthreads=25 \
  -Jloops=1000 \
  -l results/latency-results.jtl \
  -j logs/latency-test.log \
  -e -o reports/latency-report
```

**Stress Test:**
```bash
jmeter -n -t stress-test.jmx \
  -Jhost=localhost \
  -Jport=8080 \
  -Jbaseline_threads=50 \
  -Jpeak_threads=200 \
  -Jspike_threads=500 \
  -l results/stress-test-results.jtl \
  -j logs/stress-test.log \
  -e -o reports/stress-test-report
```

### Configuration Parameters

All test plans support the following command-line parameters:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `host` | Target host | `localhost` |
| `port` | Target port | `8080` |
| `threads` | Number of concurrent threads | Varies by test |
| `duration` | Test duration in seconds (throughput) | `300` |
| `loops` | Number of loops per thread (latency) | `1000` |
| `baseline_threads` | Baseline thread count (stress) | `50` |
| `peak_threads` | Peak thread count (stress) | `200` |
| `spike_threads` | Spike thread count (stress) | `500` |

### Example: Custom Configuration

```bash
# High load throughput test
jmeter -n -t throughput-test.jmx \
  -Jhost=prod-server \
  -Jport=8080 \
  -Jthreads=100 \
  -Jduration=600 \
  -l results/high-load-throughput.jtl \
  -e -o reports/high-load-report

# Low latency test with fewer threads
jmeter -n -t latency-test.jmx \
  -Jthreads=10 \
  -Jloops=500 \
  -l results/low-latency.jtl \
  -e -o reports/low-latency-report

# Extreme stress test
jmeter -n -t stress-test.jmx \
  -Jbaseline_threads=100 \
  -Jpeak_threads=500 \
  -Jspike_threads=1000 \
  -l results/extreme-stress.jtl \
  -e -o reports/extreme-stress-report
```

## Analyzing Results

### 1. HTML Dashboard Reports

When running in CLI mode with `-e -o reports/report-name`, JMeter generates comprehensive HTML reports:

```bash
# Open the report in your browser
open reports/throughput-report/index.html
```

**Key Metrics in Dashboard:**
- **APDEX (Application Performance Index):** Measures user satisfaction
- **Summary Report:** Overall statistics (samples, avg, min, max, error %, throughput)
- **Errors:** Error count and percentage by type
- **Response Times Over Time:** Time series graph
- **Throughput Over Time:** Requests per second over time
- **Response Times Percentiles:** 50th, 90th, 95th, 99th percentiles
- **Active Threads Over Time:** Concurrency visualization

### 2. CSV Results Analysis

Results are saved in JTL (CSV) format for custom analysis:

```bash
# View summary statistics
awk -F',' 'NR>1 {sum+=$2; count++} END {print "Average Response Time:", sum/count "ms"}' results/throughput-results.jtl

# Count errors
awk -F',' 'NR>1 && $8=="false" {errors++} END {print "Total Errors:", errors}' results/throughput-results.jtl

# Calculate percentiles
awk -F',' 'NR>1 {print $2}' results/latency-results.jtl | sort -n | awk 'BEGIN{c=0}{a[c]=$1;c++}END{print "P95:",a[int(c*0.95-0.5)],"ms"}'
```

### 3. Prometheus Metrics Integration

The test plans extract and log Prometheus metrics during test execution:

**Key Metrics Extracted:**
- `jobs_queue_depth` - Current queue backlog
- `jobs_worker_active` - Active worker threads
- `jobs_enqueued_total` - Total jobs enqueued
- `jobs_execution_time` - Job execution time histograms (P95, P99)
- `jobs_queue_wait_time` - Time jobs wait in queue (P95, P99)
- `jobs_completed_total{status="FAILED"}` - Failed job count

**Viewing Metrics During Test:**
```bash
# Watch queue depth in real-time
watch -n 2 "curl -s http://localhost:8080/actuator/prometheus | grep jobs_queue_depth"

# Monitor active workers
watch -n 2 "curl -s http://localhost:8080/actuator/prometheus | grep jobs_worker_active"
```

### 4. Grafana Dashboards

For real-time monitoring during tests, use the provided Grafana dashboards:

```bash
# Ensure Grafana is running
docker-compose up -d grafana

# Access Grafana
open http://localhost:3000
# Login: admin/admin
```

**Recommended Dashboards for Performance Testing:**
1. **Job Platform - System Overview:** Real-time throughput and latency
2. **Job Platform - Worker Health:** Worker saturation and queue depth
3. **Job Platform - Error Analysis:** Failure rates and exceptions

## Performance Benchmarks

### Expected Results (Reference Hardware: 4 CPU, 8GB RAM)

**Throughput Test:**
- Target: 500-1000 requests/second
- Average Response Time: < 50ms
- Error Rate: < 1%
- Queue Depth: Should remain stable (< 100)

**Latency Test:**
- Health Endpoint P95: < 10ms
- Health Endpoint P99: < 20ms
- Metrics Endpoint P95: < 50ms
- Metrics Endpoint P99: < 100ms
- Job Execution P95: < 500ms
- Job Execution P99: < 1000ms

**Stress Test:**
- Phase 1 (Baseline): Error Rate < 1%
- Phase 2 (Progressive): Error Rate < 5%
- Phase 3 (Spike): Error Rate < 20% (acceptable degradation)
- Phase 4 (Recovery): Error Rate < 1% (system recovered)
- System Recovery Time: < 2 minutes after spike

## Best Practices

### 1. Test Environment

- Use dedicated test environment (not production)
- Ensure sufficient resources (CPU, memory, network)
- Disable auto-scaling during tests for consistent results
- Use realistic data volumes in database

### 2. Test Execution

- Always warm up the system before measuring (included in test plans)
- Run tests multiple times and average results
- Monitor system resources (CPU, memory, disk I/O) during tests
- Document system configuration and test parameters

### 3. Analyzing Results

- Focus on percentiles (P95, P99) over averages
- Look for trends over time, not just final numbers
- Compare results across test runs to identify regressions
- Investigate any unexpected spikes or anomalies

### 4. CI/CD Integration

Example GitHub Actions workflow:

```yaml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly on Sunday at 2 AM
  workflow_dispatch:

jobs:
  performance-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          
      - name: Install JMeter
        run: |
          wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz
          tar -xzf apache-jmeter-5.6.3.tgz
          echo "$PWD/apache-jmeter-5.6.3/bin" >> $GITHUB_PATH
          
      - name: Start Services
        run: |
          docker-compose up -d
          mvn clean install
          cd reference-app && mvn spring-boot:run &
          sleep 30
          
      - name: Run Throughput Test
        run: |
          cd performance-tests/jmeter
          jmeter -n -t throughput-test.jmx \
            -l results/throughput.jtl \
            -e -o reports/throughput
            
      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: performance-test-results
          path: performance-tests/jmeter/reports/
```

## Troubleshooting

### Common Issues

**1. Connection Refused Error**
```
Error: Connection refused
```
**Solution:** Ensure the application is running and accessible:
```bash
curl http://localhost:8080/actuator/health
```

**2. Out of Memory Errors**
```
Error: Java heap space
```
**Solution:** Increase JMeter heap size:
```bash
export HEAP="-Xms1g -Xmx4g"
jmeter -n -t test.jmx ...
```

**3. Too Many Open Files**
```
Error: Too many open files
```
**Solution:** Increase file descriptor limits:
```bash
ulimit -n 10000
```

**4. High Error Rate**
```
Error rate > 5% during normal load
```
**Solution:**
- Check application logs for errors
- Verify database connection pool settings
- Ensure sufficient worker threads are configured
- Check system resources (CPU, memory)

### Debug Mode

Run tests with debug logging:
```bash
jmeter -n -t test.jmx -Jlog_level.jmeter=DEBUG -l results.jtl -j debug.log
```

## Advanced Topics

### Custom Metrics Extraction

Add custom regex extractors in JMeter to extract additional Prometheus metrics:

1. Open test plan in JMeter GUI
2. Add "Regular Expression Extractor" to Prometheus sampler
3. Configure regex pattern: `metric_name\{.*?\}\s+([\d.]+)`
4. Reference extracted value: `${extracted_variable}`

### Integration with InfluxDB

Enable the commented-out Backend Listener in test plans to push metrics to InfluxDB:

1. Uncomment Backend Listener element
2. Start InfluxDB: `docker run -p 8086:8086 influxdb`
3. Update `influxdbUrl` in test plan
4. Run test - metrics will be pushed in real-time

### Distributed Testing

For very high load, use distributed JMeter:

1. Start JMeter servers on multiple machines
2. Configure `jmeter.properties` with server IPs
3. Run: `jmeter -n -t test.jmx -R server1,server2,server3`

## Support

For issues or questions:
1. Check application logs: `tail -f reference-app/logs/application.log`
2. Review JMeter log: `cat logs/jmeter.log`
3. Open an issue in the repository with:
   - Test plan used
   - Configuration parameters
   - Error messages
   - System specifications

## References

- [Apache JMeter Documentation](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [Job Platform Metrics Documentation](../../docs/metrics.md)
