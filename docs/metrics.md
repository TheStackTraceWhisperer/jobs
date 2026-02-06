# Job Platform Metrics Documentation

This document describes the comprehensive metrics instrumentation implemented for the job platform, organized by **The Four Golden Signals**: Latency, Traffic, Errors, and Saturation.

## Table of Contents
1. [Traffic & Throughput Metrics](#traffic--throughput-metrics)
2. [Latency & Performance Metrics](#latency--performance-metrics)
3. [Saturation & Capacity Metrics](#saturation--capacity-metrics)
4. [Errors & Reliability Metrics](#errors--reliability-metrics)
5. [Engine Health Metrics](#engine-health-metrics)
6. [Grafana Dashboard Recommendations](#grafana-dashboard-recommendations)

## Traffic & Throughput Metrics

These counters answer: **"How much work are we doing?"**

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `jobs.enqueued.total` | Counter | `job_type`, `queue` | Total jobs pushed to the database. Correlates with API traffic. |
| `jobs.started.total` | Counter | `job_type`, `queue` | Number of jobs picked up by the poller and handed to a thread. |
| `jobs.completed.total` | Counter | `job_type`, `queue`, `status` | Jobs completed. Status: `SUCCESS`, `FAILED`, `PERMANENTLY_FAILED`, `SNOOZED` |

### Example Queries
```promql
# Total jobs enqueued per minute
rate(jobs_enqueued_total[1m])

# Completion rate by status
rate(jobs_completed_total{status="SUCCESS"}[5m])

# Queue-specific throughput
rate(jobs_started_total{queue="DEFAULT"}[1m])
```

## Latency & Performance Metrics

These timers answer: **"How fast is the work happening?"**

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `jobs.execution.time` | Timer | `job_type`, `status` | How long `handler.handle()` takes. Critical for timeout tuning. |
| `jobs.queue.wait.time` | Timer | `job_type`, `queue` | Time between `run_at` (scheduled) and actual start. Measures platform lag. |
| `jobs.db.poll.time` | Timer | `queue` | How long `SELECT ... FOR UPDATE` takes. Spikes indicate DB index issues. |
| `jobs.enqueue.time` | Timer | `job_type` | Time to serialize JSON and INSERT into DB. |

### Example Queries
```promql
# P95 execution time by job type
histogram_quantile(0.95, rate(jobs_execution_time_bucket[5m]))

# P99 queue wait time (detect backlog)
histogram_quantile(0.99, rate(jobs_queue_wait_time_bucket[5m]))

# DB polling performance
rate(jobs_db_poll_time_sum[1m]) / rate(jobs_db_poll_time_count[1m])
```

## Saturation & Capacity Metrics

These gauges answer: **"Are we full?"**

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `jobs.worker.active` | Gauge | `queue` | Current number of threads actively running a job (0 to max_permits). |
| `jobs.worker.permits.available` | Gauge | `queue` | Inverse of above. Used to alert on starvation. |
| `jobs.queue.depth` | Gauge | `queue` | **The Holy Grail.** Total count of QUEUED jobs in the DB. |
| `jobs.queue.oldest.age` | Gauge | `queue` | Age (in seconds) of the oldest QUEUED job. Better than depth for stuck jobs. |

### Example Queries
```promql
# Worker saturation percentage
(jobs_worker_active / (jobs_worker_active + jobs_worker_permits_available)) * 100

# Queue backlog alert (depth > 1000)
jobs_queue_depth > 1000

# Stuck job detection (oldest job > 5 minutes)
jobs_queue_oldest_age > 300
```

## Errors & Reliability Metrics

These counters answer: **"Is it broken?"**

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `jobs.failures.exception` | Counter | `job_type`, `exception_class` | Counts specific Java exceptions (e.g., `NullPointerException`, `OptimisticLockingFailure`). |
| `jobs.retries.total` | Counter | `job_type` | How many times jobs re-enter the queue due to failures. |
| `jobs.deadletter.total` | Counter | `job_type` | Jobs that hit max attempts and moved to `PERMANENTLY_FAILED`. |

### Example Queries
```promql
# Error rate by exception type
rate(jobs_failures_exception[5m])

# Retry rate (indicates flaky jobs)
rate(jobs_retries_total[5m])

# Dead letter rate (permanent failures)
rate(jobs_deadletter_total[5m])
```

## Engine Health Metrics

These "meta metrics" answer: **"Is the platform itself healthy?"**

| Metric Name | Type | Tags | Description |
|---|---|---|---|
| `jobs.poller.loops` | Counter | `outcome` | Outcome: `found_jobs`, `empty`, `skipped_full`. Tracks poller behavior. |
| `jobs.reaper.zombies` | Counter | `queue` | How many "stuck" jobs the MaintenanceService reset. Spikes indicate crashing pods. |
| `jobs.reaper.execution.time` | Timer | - | How long the cleanup query takes. |

### Example Queries
```promql
# Poller effectiveness (finding jobs)
rate(jobs_poller_loops{outcome="found_jobs"}[1m])

# Worker starvation (polls skipped due to full capacity)
rate(jobs_poller_loops{outcome="skipped_full"}[1m])

# Zombie detection rate (pod crashes)
rate(jobs_reaper_zombies[5m])
```

## Implementation Details

### Metrics Service
All metrics are recorded through the centralized `JobMetricsService` class located in:
```
job-platform-common/src/main/java/io/github/thestacktracewhisperer/jobs/common/metrics/JobMetricsService.java
```

This service uses Micrometer's `MeterRegistry` and provides convenient methods for recording all metrics with proper tags.

### Key Integration Points

#### 1. BackgroundWorker
- Records polling metrics (`jobs.poller.loops`, `jobs.db.poll.time`)
- Records job start (`jobs.started.total`)
- Records execution metrics (`jobs.execution.time`, `jobs.queue.wait.time`)
- Records completion status (`jobs.completed.total`)
- Records failures and retries (`jobs.failures.exception`, `jobs.retries.total`, `jobs.deadletter.total`)
- Maintains saturation gauges (`jobs.worker.active`, `jobs.worker.permits.available`)

#### 2. JobEnqueuer
- Records enqueue time (`jobs.enqueue.time`)
- Records enqueue count (`jobs.enqueued.total`)

#### 3. MaintenanceService
- Records zombie reaping (`jobs.reaper.zombies`, `jobs.reaper.execution.time`)
- Periodically updates queue metrics (`jobs.queue.depth`, `jobs.queue.oldest.age`)

### Queue Metrics Performance
The `jobs.queue.depth` and `jobs.queue.oldest.age` metrics are updated via a **separate scheduled task** that runs every 15 seconds (configurable via `platform.jobs.worker.queue-metrics-interval-ms`). This prevents expensive `COUNT(*)` queries from impacting the main polling loop.

The implementation uses a native SQL query for efficiency:
```sql
SELECT queue_name, COUNT(queue_name), MAX(DATEDIFF(SECOND, created_at, GETDATE()))
FROM background_jobs
WHERE status = 'QUEUED'
GROUP BY queue_name
```

## Grafana Dashboard Recommendations

### Top Row (Health Overview)
- **Active Workers** (Gauge) - `jobs.worker.active`
- **Queue Depth** (Gauge) - `jobs.queue.depth`
- **Error Rate** (%) - `rate(jobs_completed_total{status="FAILED"}[5m]) / rate(jobs_completed_total[5m]) * 100`

### Row 2 (Throughput)
- **Enqueued vs. Completed** (Time Series)
  - `rate(jobs_enqueued_total[1m])`
  - `rate(jobs_completed_total[1m])`

### Row 3 (Latency)
- **Execution Time P95 & P99** (Time Series)
  - `histogram_quantile(0.95, rate(jobs_execution_time_bucket[5m]))`
  - `histogram_quantile(0.99, rate(jobs_execution_time_bucket[5m]))`
- **Queue Wait Time P99** (Time Series)
  - `histogram_quantile(0.99, rate(jobs_queue_wait_time_bucket[5m]))`

### Row 4 (Errors & Retries)
- **Retry Rate** (Time Series) - `rate(jobs_retries_total[5m])`
- **Dead Letter Rate** (Time Series) - `rate(jobs_deadletter_total[5m])`
- **Exception Breakdown** (Pie Chart) - `sum by (exception_class) (jobs_failures_exception)`

### Row 5 (Engine Health)
- **Poller Outcomes** (Stacked Area)
  - `rate(jobs_poller_loops{outcome="found_jobs"}[1m])`
  - `rate(jobs_poller_loops{outcome="empty"}[1m])`
  - `rate(jobs_poller_loops{outcome="skipped_full"}[1m])`
- **Zombie Jobs** (Time Series) - `rate(jobs_reaper_zombies[5m])`

## Alerting Recommendations

### Critical Alerts
```yaml
# Queue depth exceeds 1000
- alert: HighQueueDepth
  expr: jobs_queue_depth > 1000
  for: 5m

# Oldest job stuck for more than 10 minutes
- alert: StuckJobs
  expr: jobs_queue_oldest_age > 600
  for: 2m

# Worker saturation > 90%
- alert: WorkerSaturation
  expr: (jobs_worker_active / (jobs_worker_active + jobs_worker_permits_available)) > 0.9
  for: 5m
```

### Warning Alerts
```yaml
# High retry rate (> 10% of completions)
- alert: HighRetryRate
  expr: rate(jobs_retries_total[5m]) / rate(jobs_completed_total[5m]) > 0.1
  for: 10m

# Zombie jobs detected
- alert: ZombieJobsDetected
  expr: rate(jobs_reaper_zombies[5m]) > 0
  for: 5m

# DB poll time > 100ms (p95)
- alert: SlowDbPolling
  expr: histogram_quantile(0.95, rate(jobs_db_poll_time_bucket[5m])) > 0.1
  for: 5m
```

## Configuration

All metrics are automatically enabled when using the job platform. The following properties can be configured:

```yaml
platform:
  jobs:
    worker:
      # How often to update queue depth metrics (default: 15s)
      queue-metrics-interval-ms: 15000
      
      # How often to run zombie job reaping (default: 60s)
      reaper-interval-ms: 60000
      
      # How often to poll for jobs (default: 1s)
      polling-interval-ms: 1000
```

## Metrics Export

The platform uses Micrometer with Prometheus registry. Metrics are exposed via the standard Spring Boot Actuator endpoint:

```
GET /actuator/prometheus
```

Ensure your `application.yml` includes:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```
