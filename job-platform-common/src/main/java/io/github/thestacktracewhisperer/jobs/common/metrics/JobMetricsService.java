package io.github.thestacktracewhisperer.jobs.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized service for recording job platform metrics.
 * Implements The Four Golden Signals: Latency, Traffic, Errors, and Saturation.
 */
@Service
@RequiredArgsConstructor
public class JobMetricsService {

    private final MeterRegistry meterRegistry;
    
    // Cache for counters to avoid recreating them
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    // ============================================================================
    // TRAFFIC & THROUGHPUT METRICS (Counters)
    // ============================================================================

    /**
     * Records when a job is enqueued to the database.
     */
    public void recordJobEnqueued(String jobType, String queueName) {
        getCounter("jobs.enqueued.total", 
            "Total jobs pushed to the DB",
            Tags.of("job_type", jobType, "queue", queueName))
            .increment();
    }

    /**
     * Records when a job is picked up by the poller and handed to a thread.
     */
    public void recordJobStarted(String jobType, String queueName) {
        getCounter("jobs.started.total",
            "Number of jobs picked up by the poller",
            Tags.of("job_type", jobType, "queue", queueName))
            .increment();
    }

    /**
     * Records job completion with status.
     */
    public void recordJobCompleted(String jobType, String queueName, String status) {
        getCounter("jobs.completed.total",
            "Total completed jobs by status",
            Tags.of("job_type", jobType, "queue", queueName, "status", status))
            .increment();
    }

    /**
     * Records saga compensation execution (undo workflow).
     */
    public void recordSagaCompensation(String jobType) {
        getCounter("jobs.saga.compensations.total",
            "Number of times a Saga triggered its Undo workflow",
            Tags.of("job_type", jobType))
            .increment();
    }

    // ============================================================================
    // LATENCY & PERFORMANCE METRICS (Timers)
    // ============================================================================

    /**
     * Records job execution time.
     */
    public void recordExecutionTime(String jobType, String status, Duration duration) {
        getTimer("jobs.execution.time",
            "Job execution time",
            Tags.of("job_type", jobType, "status", status))
            .record(duration);
    }

    /**
     * Records queue wait time (time between scheduled run_at and actual start).
     */
    public void recordQueueWaitTime(String jobType, String queueName, Duration waitTime) {
        getTimer("jobs.queue.wait.time",
            "Time between run_at and actual start time",
            Tags.of("job_type", jobType, "queue", queueName))
            .record(waitTime);
    }

    /**
     * Records database poll time.
     */
    public void recordDbPollTime(String queueName, Duration pollTime) {
        getTimer("jobs.db.poll.time",
            "Time for SQL SELECT ... FOR UPDATE",
            Tags.of("queue", queueName))
            .record(pollTime);
    }

    /**
     * Records job enqueue time (serialization + DB insert).
     */
    public void recordEnqueueTime(String jobType, Duration enqueueTime) {
        getTimer("jobs.enqueue.time",
            "Time to serialize and INSERT job",
            Tags.of("job_type", jobType))
            .record(enqueueTime);
    }

    // ============================================================================
    // ERRORS & RELIABILITY METRICS (Counters)
    // ============================================================================

    /**
     * Records job failure by exception type.
     */
    public void recordJobException(String jobType, String exceptionClass) {
        getCounter("jobs.failures.exception",
            "Counts specific Java exceptions",
            Tags.of("job_type", jobType, "exception_class", exceptionClass))
            .increment();
    }

    /**
     * Records job retry attempts.
     */
    public void recordJobRetry(String jobType) {
        getCounter("jobs.retries.total",
            "Jobs re-entering queue due to failure",
            Tags.of("job_type", jobType))
            .increment();
    }

    /**
     * Records jobs moved to dead letter (permanently failed).
     */
    public void recordDeadLetter(String jobType) {
        getCounter("jobs.deadletter.total",
            "Jobs that hit max attempts",
            Tags.of("job_type", jobType))
            .increment();
    }

    // ============================================================================
    // ENGINE HEALTH METRICS
    // ============================================================================

    /**
     * Records poller loop outcomes.
     */
    public void recordPollerLoop(String outcome) {
        getCounter("jobs.poller.loops",
            "Tracks poller behavior",
            Tags.of("outcome", outcome))
            .increment();
    }

    /**
     * Records zombie jobs detected and reset.
     */
    public void recordZombieJobReaped(String queueName) {
        getCounter("jobs.reaper.zombies",
            "Stuck jobs reset by maintenance",
            Tags.of("queue", queueName))
            .increment();
    }

    /**
     * Records reaper execution time.
     */
    public void recordReaperExecutionTime(Duration duration) {
        getTimer("jobs.reaper.execution.time",
            "Time for cleanup query",
            Tags.empty())
            .record(duration);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Counter getCounter(String name, String description, Tags tags) {
        String cacheKey = name + tags.toString();
        return counterCache.computeIfAbsent(cacheKey, k ->
            Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry)
        );
    }

    private Timer getTimer(String name, String description, Tags tags) {
        String cacheKey = name + tags.toString();
        return timerCache.computeIfAbsent(cacheKey, k ->
            Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry)
        );
    }
}
