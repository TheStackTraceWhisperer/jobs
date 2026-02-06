# Job Platform Observability

This directory contains pre-configured monitoring infrastructure for the Job Platform using Prometheus and Grafana.

## Quick Start

Start the monitoring stack:

```bash
docker-compose up -d prometheus grafana
```

Access the dashboards:
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090

## What's Included

### Prometheus
- Scrapes metrics from job platform every 15 seconds
- Pre-configured to collect metrics from port 8080
- Metrics include both API and worker operations (worker metrics are exposed via the API node)
- Configuration: `prometheus/prometheus.yml`

### Grafana
- Pre-provisioned Prometheus datasource
- Auto-loaded "Job Platform - Four Golden Signals" dashboard
- Dashboard includes:
  - **Health Overview**: Active workers, queue depth, error rate
  - **Throughput**: Jobs enqueued vs completed
  - **Latency**: P95/P99 execution time, queue wait time
  - **Reliability**: Retries, dead letter queue
  - **Engine Health**: Poller behavior

### Dashboard Panels

#### Row 1: Health Overview
- **Active Workers** (Gauge): Current number of threads processing jobs
- **Queue Depth** (Gauge): Total queued jobs across all queues
- **Error Rate** (Time Series): Percentage of failed jobs

#### Row 2: Throughput
- **Job Throughput**: Enqueued vs completed jobs per minute
- **Job Completion by Status**: Stacked view of SUCCESS/FAILED/PERMANENTLY_FAILED

#### Row 3: Latency
- **Job Execution Latency**: P95 and P99 execution times
- **Queue Wait Time**: P99 time jobs spend waiting in queue

#### Row 4: Reliability
- **Job Retries & Dead Letter**: Rate of retries and permanently failed jobs
- **Poller Health**: Behavior of job polling (found_jobs/empty/skipped_full)

## Directory Structure

```
observability/
├── prometheus/
│   └── prometheus.yml           # Prometheus configuration
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/
│   │   │   └── prometheus.yml   # Auto-configured Prometheus datasource
│   │   └── dashboards/
│   │       └── dashboards.yml   # Dashboard provisioning config
│   └── dashboards/
│       └── job-platform-metrics.json  # Pre-built dashboard
└── README.md
```

## Configuration

### Adding New Scrape Targets

Edit `prometheus/prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'my-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['my-service:8080']
```

### Customizing Dashboards

1. Log in to Grafana (http://localhost:3000)
2. Navigate to the dashboard
3. Click the gear icon → Settings → JSON Model
4. Copy the JSON to `grafana/dashboards/`

### Retention

By default, Prometheus retains data for 15 days. To change:

```yaml
prometheus:
  command:
    - '--storage.tsdb.retention.time=30d'  # 30 days
```

## Metrics Reference

See [docs/metrics.md](../docs/metrics.md) for detailed metric descriptions, PromQL queries, and alerting recommendations.

## Troubleshooting

### No data in Grafana
1. Check Prometheus targets: http://localhost:9090/targets
2. Verify job platform services expose `/actuator/prometheus`
3. Ensure services are reachable from Prometheus container

### Dashboard not loading
1. Check Grafana logs: `docker-compose logs grafana`
2. Verify dashboard JSON is valid
3. Restart Grafana: `docker-compose restart grafana`

### High memory usage
- Reduce scrape interval in `prometheus.yml`
- Decrease retention time
- Add recording rules to pre-aggregate data
