# End-to-End Orchestration Test Implementation

## Overview
This document describes the comprehensive end-to-end orchestration test that validates multi-step job processing with various failure scenarios including temporary failures, permanent failures, requeue operations, and successful completions.

## Test Structure

### Test Class: `OrchestrationEndToEndTest`
Location: `reference-app/src/test/java/io/github/thestacktracewhisperer/jobs/reference/OrchestrationEndToEndTest.java`

The test demonstrates a complete workflow from API request → Job Queue → Worker Processing with the following components:

## Components

### 1. Job Definition: `OrchestratedTask`
A test job record that simulates orchestrated tasks with configurable execution behaviors:
- **requestIdentifier**: Unique identifier for tracking the request
- **operationName**: Name of the operation being performed
- **executionBehavior**: Defines how the job should behave (success, temporary failure, permanent failure)
- **retriesBeforeSuccess**: Number of retries before succeeding (for temporary failure scenarios)

### 2. Handler: `OrchestratedTaskHandler`
Implements `JobHandler<OrchestratedTask>` and simulates real-world processing with:
- **Attempt tracking**: Counts how many times each job has been processed
- **Success tracking**: Records which jobs completed successfully
- **Failure simulation**: Implements three execution modes

### 3. Execution Modes
- **IMMEDIATE_SUCCESS**: Job completes successfully on first attempt
- **FAIL_TWICE_THEN_SUCCEED**: Job fails with "Transient failure - network timeout" for configured attempts, then succeeds
- **PERMANENT_HARDWARE_FAILURE**: Job always fails with "Hardware malfunction - disk sector unreadable"

## Test Scenarios

### Scenario 1: Successful End-to-End Orchestration
- Controller receives request and enqueues job
- Worker picks up the job from the queue
- Job processes successfully on first attempt
- Validates:
  - Job was enqueued with QUEUED status
  - Job was processed exactly once
  - Job completed successfully

### Scenario 2: Temporary Failure with Requeue and Recovery
- Job configured to fail twice before succeeding
- First attempt: Throws exception, increments attempt counter
- Second attempt: Throws exception again
- Third attempt: Succeeds and completes
- Validates:
  - Proper exception handling on failures
  - Attempt counter increments correctly
  - Job eventually succeeds after retries

### Scenario 3: Permanent Failure Exhausts Retries
- Job configured to always fail
- Multiple attempts all result in same hardware failure error
- Validates:
  - Job fails consistently
  - Attempt counter increments with each failure
  - Job never completes successfully

### Scenario 4: Multi-Step Orchestration Pipeline
- Controller receives multiple requests simultaneously
- Three different jobs with different behaviors:
  - Job 0: Immediate success
  - Job 1: Temporary failure (2 retries then success)
  - Job 2: Permanent failure
- All jobs are queued together
- Worker processes each according to its configured behavior
- Validates:
  - All jobs are queued correctly
  - Each job follows its expected execution path
  - Independent job failures don't affect other jobs

## Configuration

### Test Profile: `application-orchestration.properties`
Location: `reference-app/src/test/resources/application-orchestration.properties`

Key configurations:
- **H2 In-Memory Database**: Fast, isolated test database
- **Worker Enabled**: Unlike standard tests, worker is enabled to process jobs
- **Worker Settings**:
  - Concurrency: 5 workers
  - Max attempts: 3
  - Polling interval: 100ms (fast for testing)
- **Debug Logging**: Enabled for troubleshooting

## Test Execution

Run the test:
```bash
mvn test -Dtest=OrchestrationEndToEndTest -pl reference-app
```

## What This Test Validates

### Orchestration Flow
✅ API layer (JobEnqueuer) successfully enqueues jobs
✅ Jobs are persisted to database with correct status
✅ Worker layer polls and retrieves jobs
✅ JobRoutingEngine correctly dispatches to handlers
✅ Handler execution with various outcomes

### Failure Handling
✅ Temporary failures trigger requeue with backoff
✅ Attempt counters increment properly
✅ Permanent failures are handled gracefully
✅ Independent job processing (one failure doesn't affect others)

### State Management
✅ Job status transitions (QUEUED → PROCESSING → SUCCESS/FAILED)
✅ Attempt tracking across retries
✅ Request identifier tracking for correlation

## Architecture Benefits Demonstrated

1. **Separation of Concerns**: Controller, queue, and worker are independent
2. **Resilience**: Automatic retry with exponential backoff
3. **Observability**: Each step is logged and trackable
4. **Testability**: Can test complex scenarios in isolation
5. **Scalability**: Multiple workers process jobs concurrently

## Future Enhancements

Potential additions to this test:
- Dead letter queue validation
- Saga pattern compensation testing
- Fan-out pattern with child jobs
- Scheduled job execution (delayed jobs)
- Priority queue handling
- Zombie job reaping
