package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.worker.dispatcher.JobRoutingEngine;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that polls for jobs and executes them.
 * Uses a semaphore to limit concurrent job execution.
 */
@Component
@ConditionalOnProperty(prefix = "platform.jobs.worker", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BackgroundWorker {

    private static final Logger log = LoggerFactory.getLogger(BackgroundWorker.class);
    private static final int FORCED_SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final JobRepository jobRepository;
    private final JobRoutingEngine routingEngine;
    private final JobWorkerProperties properties;
    private final JobEnqueuer jobEnqueuer;
    private final JobClaimService jobClaimService;
    private final JobMetricsService metricsService;
    
    private Semaphore semaphore;
    private ExecutorService executorService;
    private java.util.Set<String> supportedJobTypes;

    @PostConstruct
    public void initialize() {
        this.semaphore = new Semaphore(properties.getConcurrency());
        this.executorService = Executors.newFixedThreadPool(properties.getConcurrency());
        
        // Discover which job types this worker can handle
        this.supportedJobTypes = routingEngine.getRegisteredJobTypes();
        log.info("Worker supports {} job types: {}", supportedJobTypes.size(), supportedJobTypes);
        
        // Initialize saturation metrics (gauges)
        metricsService.registerWorkerActiveGauge(properties.getQueueName(), semaphore, properties.getConcurrency());
        metricsService.registerWorkerPermitsAvailableGauge(properties.getQueueName(), semaphore);
            
        log.info("Background worker initialized with concurrency: {}", properties.getConcurrency());
    }

    /**
     * Polls for jobs and executes them.
     * Runs on a fixed delay schedule.
     */
    @Scheduled(fixedDelayString = "${platform.jobs.worker.polling-interval-ms:1000}")
    public void pollAndExecute() {
        // Guard: Skip if no permits available
        int availablePermits = semaphore.availablePermits();
        if (availablePermits == 0) {
            metricsService.recordPollerLoop("skipped_full");
            return;
        }

        try {
            // Measure DB poll time
            Instant pollStart = Instant.now();
            
            // Delegate to service for proper transaction handling
            List<JobEntity> claimedJobs = jobClaimService.fetchAndClaimJobs(
                supportedJobTypes,
                properties.getQueueName(),
                availablePermits
            );
            
            Duration pollDuration = Duration.between(pollStart, Instant.now());
            metricsService.recordDbPollTime(properties.getQueueName(), pollDuration);
            
            if (claimedJobs.isEmpty()) {
                metricsService.recordPollerLoop("empty");
                return;
            }

            metricsService.recordPollerLoop("found_jobs");
            log.debug("Claimed {} jobs for processing", claimedJobs.size());

            // Process each claimed job asynchronously
            for (JobEntity job : claimedJobs) {
                if (semaphore.tryAcquire()) {
                    // Record that job was started
                    metricsService.recordJobStarted(job.getJobType(), job.getQueueName());
                    
                    // Submit job to executor service for processing
                    executorService.submit(() -> executeClaimedJob(job));
                } else {
                    break; // No more permits available
                }
            }
        } catch (Exception e) {
            log.error("Error during job polling", e);
        }
    }

    /**
     * Processes a job that has already been claimed.
     */
    private void executeClaimedJob(JobEntity job) {
        Instant executionStart = Instant.now();
        String status = "FAILED";
        
        try {
            // Set job context for parent-child tracking
            JobContextHolder.setCurrentJobId(job.getId());
            
            log.info("Executing job: id={}, type={}, attempt={}", 
                job.getId(), job.getJobType(), job.getAttempts());

            // Calculate and record queue wait time
            Duration waitTime = Duration.between(job.getRunAt(), executionStart);
            metricsService.recordQueueWaitTime(job.getJobType(), job.getQueueName(), waitTime);

            // Route and execute the job
            routingEngine.route(job.getJobType(), job.getPayload());

            // Delegate to service for proper transaction handling
            jobClaimService.markJobSuccess(job.getId());
            
            status = "SUCCESS";
            metricsService.recordJobCompleted(job.getJobType(), job.getQueueName(), status);
            
            log.info("Job completed successfully: id={}", job.getId());

        } catch (JobSnoozeException e) {
            log.info("Job snoozing: id={}, delay={}", job.getId(), e.getDelay());
            
            // Delegate to service for proper transaction handling
            jobClaimService.handleJobSnooze(job.getId(), e);
            
            status = "SNOOZED";
            metricsService.recordJobCompleted(job.getJobType(), job.getQueueName(), status);

        } catch (Exception e) {
            log.error("Job execution failed: id={}, type={}, error={}", 
                job.getId(), job.getJobType(), e.getMessage(), e);
            
            // Record exception type
            metricsService.recordJobException(job.getJobType(), e.getClass().getSimpleName());
            
            // Delegate to service for proper transaction handling
            jobClaimService.handleJobFailure(job.getId(), e.getMessage(), properties.getMaxAttempts());
            
            // Check if permanently failed or will retry
            if (job.getAttempts() >= properties.getMaxAttempts()) {
                status = "PERMANENTLY_FAILED";
                metricsService.recordDeadLetter(job.getJobType());
            } else {
                status = "FAILED";
                metricsService.recordJobRetry(job.getJobType());
            }
            
            metricsService.recordJobCompleted(job.getJobType(), job.getQueueName(), status);
            
        } finally {
            Duration executionDuration = Duration.between(executionStart, Instant.now());
            metricsService.recordExecutionTime(job.getJobType(), status, executionDuration);
            
            JobContextHolder.clear();
            semaphore.release();
        }
    }

    /**
     * Gracefully shuts down the executor service when the application stops.
     * Waits for running jobs to complete before forcing shutdown.
     */
    @PreDestroy
    public void shutdown() {
        if (executorService == null) {
            return;
        }

        log.info("Shutting down worker executor service...");
        
        // Stop accepting new tasks
        executorService.shutdown();
        
        try {
            // Wait for running jobs to complete
            long timeoutSeconds = properties.getShutdownTimeoutSeconds();
            log.info("Waiting up to {} seconds for running jobs to complete", timeoutSeconds);
            
            boolean terminated = executorService.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
            
            if (terminated) {
                log.info("All jobs completed successfully during shutdown");
            } else {
                log.warn("Shutdown timeout reached. Forcing shutdown of executor service.");
                executorService.shutdownNow();
                
                // Wait a bit more for forced shutdown
                if (executorService.awaitTermination(FORCED_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    log.info("Executor service forcefully shut down");
                } else {
                    log.error("Executor service did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted. Forcing shutdown.", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
