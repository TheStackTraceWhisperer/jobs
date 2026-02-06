# Enterprise Distributed Job Orchestration Platform

A high-performance, FOSS (Free and Open Source Software) distributed background job system built with Java 17, Spring Boot 3.4+, and MSSQL Server.

## Features

- **Transactional Integrity**: Job enqueueing is atomic with business logic (Outbox Pattern)
- **Polymorphic Deployment**: Single codebase can act as API Node or Worker Node via Spring Profiles
- **Zero-Dependency Logic**: Business logic decoupled from execution engine
- **Resiliency**: Built-in exponential backoff, dead-letter queuing (DLQ), and zombie reaping
- **Job Snooze Pattern**: Jobs can defer execution without failure penalties
- **Fan-Out Pattern**: Distribute work across multiple child jobs
- **Observability**: Prometheus metrics via Micrometer
- **Race-Free**: Pessimistic locking prevents duplicate job processing

## Architecture

The project follows a multi-module Maven structure:

```
job-platform-parent/
├── job-platform-common            # Core contracts and entities
├── job-platform-producer-starter  # Job enqueueing
├── job-platform-worker-starter    # Job execution engine
└── reference-app                  # Example implementation
```

## Quick Start

### Prerequisites

- Java 17 (LTS)
- Maven 3.9+
- Docker & Docker Compose

### 1. Start the Database

```bash
docker-compose up -d
```

This starts an MSSQL Server 2022 instance on port 1433.

### 2. Create the Database

```bash
docker exec -it $(docker ps -q -f name=mssql) /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P 'YourStrong!Passw0rd' \
  -Q "CREATE DATABASE jobs"
```

Apply the schema:

```bash
docker exec -i $(docker ps -q -f name=mssql) /opt/mssql-tools/bin/sqlcmd \
  -S localhost -U sa -P 'YourStrong!Passw0rd' \
  < database/schema.sql
```

### 3. Build the Project

```bash
mvn clean install
```

### 4. Run the Application

**As API Node (produces jobs, doesn't process them):**

```bash
cd reference-app
mvn spring-boot:run -Dspring-boot.run.profiles=api
```

The API will be available at http://localhost:8080

**As Worker Node (processes jobs):**

```bash
cd reference-app
mvn spring-boot:run -Dspring-boot.run.profiles=worker
```

**As Both (for development):**

```bash
cd reference-app
mvn spring-boot:run
```

## Example Usage

### Enqueuing a Simple Job

```java
@RestController
@RequiredArgsConstructor
public class JobController {
    
    private final JobEnqueuer enqueuer;
    
    @PostMapping("/jobs/hello")
    public ResponseEntity<Void> enqueueHelloWorld() {
        enqueuer.enqueue(new HelloWorldJob("Hello from API!"));
        return ResponseEntity.accepted().build();
    }
}
```

### Fan-Out Pattern (Monthly Reports)

```java
@Component
public class ReportSplitterHandler implements JobHandler<GenerateMonthlyReportJob> {
    private final JobEnqueuer enqueuer;
    private final UserRepository userRepo;
    
    @Override
    public void handle(GenerateMonthlyReportJob job) throws Exception {
        // Stream users to avoid loading all into memory
        userRepo.streamActiveUserIds().forEach(userId -> {
            enqueuer.enqueue(new UserReportJob(job.reportId(), userId));
        });
        
        // Enqueue finalization job
        enqueuer.enqueue(new FinalizeReportJob(job.reportId()));
    }
}
```

### Job Snooze Pattern (Self-Deferral)

Jobs can voluntarily defer execution without counting as failures:

```java
@Component
public class FinalizeReportHandler implements JobHandler<FinalizeReportJob> {
    private final JobRepository jobRepository;
    
    @Override
    public void handle(FinalizeReportJob job) throws Exception {
        // Check if dependent jobs are complete
        long pending = jobRepository.countByStatusAndBatchId(
            JobStatus.QUEUED, job.reportId()
        ) + jobRepository.countByStatusAndBatchId(
            JobStatus.PROCESSING, job.reportId()
        );
        
        if (pending > 0) {
            // Defer execution for 30 seconds - does NOT increment attempt counter
            throw new JobSnoozeException(
                "Waiting for " + pending + " sub-tasks",
                Duration.ofSeconds(30)
            );
        }
        
        // All dependencies complete - proceed with aggregation
        generateFinalReport(job.reportId());
    }
}
```

## Configuration

### Worker Properties

```properties
platform.jobs.worker.enabled=true
platform.jobs.worker.concurrency=10
platform.jobs.worker.queue-name=DEFAULT
platform.jobs.worker.max-attempts=3
platform.jobs.worker.zombie-threshold-minutes=5
platform.jobs.worker.polling-interval-ms=1000
platform.jobs.worker.reaper-interval-ms=60000
```

## Metrics

The platform exposes Prometheus metrics at `/actuator/prometheus`:

- `jobs.active.count` - Current number of jobs being processed
- `jobs.completed.total{status}` - Total completed jobs by status
- `jobs.execution.time` - Job execution duration histogram

## Testing

Run the test suite:

```bash
mvn test
```

Integration tests use Testcontainers to spin up an MSSQL instance automatically.

## Technology Stack

- **Java 17** - Utilizing Records, Sealed Classes, Pattern Matching
- **Spring Boot 3.4+** - Application framework
- **MSSQL Server 2019/2022** - Persistent storage
- **Maven 3.9+** - Build tool
- **JUnit 5 + Testcontainers** - Testing
- **JaCoCo** - Code coverage (80% minimum)
- **Micrometer** - Observability

## License

This project is licensed under the Apache License 2.0.

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.