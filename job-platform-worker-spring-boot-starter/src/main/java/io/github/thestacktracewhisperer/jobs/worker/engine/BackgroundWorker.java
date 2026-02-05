package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.worker.dispatcher.JobRoutingEngine;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Background worker that polls for jobs and executes them.
 * Uses a semaphore to limit concurrent job execution.
 */
@Component
@ConditionalOnProperty(prefix = "platform.jobs.worker", name = "enabled", havingValue = "true")
public class BackgroundWorker {

    private static final Logger log = LoggerFactory.getLogger(BackgroundWorker.class);

    private final JobRepository jobRepository;
    private final JobRoutingEngine routingEngine;
    private final JobWorkerProperties properties;
    private final JobEnqueuer jobEnqueuer;
    private final MeterRegistry meterRegistry;
    
    private Semaphore semaphore;
    private ExecutorService executorService;
    private Counter successCounter;
    private Counter failureCounter;
    private Counter permanentFailureCounter;
    private Timer executionTimer;
    private java.util.Set<String> supportedJobTypes;

    public BackgroundWorker(
            JobRepository jobRepository,
            JobRoutingEngine routingEngine,
            JobWorkerProperties properties,
            JobEnqueuer jobEnqueuer,
            MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.routingEngine = routingEngine;
        this.properties = properties;
        this.jobEnqueuer = jobEnqueuer;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initialize() {
        this.semaphore = new Semaphore(properties.getConcurrency());
        this.executorService = Executors.newFixedThreadPool(properties.getConcurrency());
        
        // Discover which job types this worker can handle
        this.supportedJobTypes = routingEngine.getRegisteredJobTypes();
        log.info("Worker supports {} job types: {}", supportedJobTypes.size(), supportedJobTypes);
        
        // Initialize metrics
        this.successCounter = Counter.builder("jobs.completed.total")
            .tag("status", "success")
            .description("Total number of successfully completed jobs")
            .register(meterRegistry);
            
        this.failureCounter = Counter.builder("jobs.completed.total")
            .tag("status", "failed")
            .description("Total number of failed jobs")
            .register(meterRegistry);
            
        this.permanentFailureCounter = Counter.builder("jobs.completed.total")
            .tag("status", "permanently_failed")
            .description("Total number of permanently failed jobs")
            .register(meterRegistry);
            
        this.executionTimer = Timer.builder("jobs.execution.time")
            .description("Job execution time")
            .register(meterRegistry);
            
        meterRegistry.gauge("jobs.active.count", semaphore, 
            s -> properties.getConcurrency() - s.availablePermits());
            
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
            return;
        }

        try {
            // Fetch and claim jobs atomically to prevent race conditions
            List<JobEntity> claimedJobs = claimJobsAtomically(availablePermits);
            
            if (claimedJobs.isEmpty()) {
                return;
            }

            log.debug("Claimed {} jobs for processing", claimedJobs.size());

            // Process each claimed job asynchronously
            for (JobEntity job : claimedJobs) {
                if (semaphore.tryAcquire()) {
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
     * Atomically fetches and claims jobs to prevent race conditions between workers.
     * Only fetches jobs for which this worker has registered handlers.
     */
    @Transactional
    protected List<JobEntity> claimJobsAtomically(int batchSize) {
        if (supportedJobTypes.isEmpty()) {
            log.warn("No job handlers registered, skipping poll");
            return java.util.Collections.emptyList();
        }
        
        // Query with pessimistic lock and job type filter
        org.springframework.data.domain.Pageable pageRequest = 
            org.springframework.data.domain.PageRequest.of(0, batchSize);
            
        org.springframework.data.domain.Page<JobEntity> candidatePage = 
            jobRepository.findJobsReadyForProcessing(
                JobStatus.QUEUED,
                properties.getQueueName(),
                LocalDateTime.now(),
                supportedJobTypes,
                pageRequest
            );
        
        List<JobEntity> candidates = candidatePage.getContent();
        
        // Atomically claim by updating status
        List<JobEntity> claimed = new java.util.ArrayList<>();
        for (JobEntity candidate : candidates) {
            try {
                // Verify current state before claiming
                JobEntity current = jobRepository.findById(candidate.getId()).orElse(null);
                    
                if (current != null && current.getStatus() == JobStatus.QUEUED) {
                    current.incrementAttempts();
                    current.markAsProcessing();
                    jobRepository.saveAndFlush(current);
                    claimed.add(current);
                }
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                log.debug("Job {} already claimed by another worker", candidate.getId());
            }
        }
        
        return claimed;
    }

    /**
     * Processes a job that has already been claimed.
     */
    private void executeClaimedJob(JobEntity job) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Set job context for parent-child tracking
            JobContextHolder.setCurrentJobId(job.getId());
            
            log.info("Executing job: id={}, type={}, attempt={}", 
                job.getId(), job.getJobType(), job.getAttempts());

            // Route and execute the job
            routingEngine.route(job.getJobType(), job.getPayload());

            // Mark as successful
            markAsSuccessful(job);
            successCounter.increment();
            
            log.info("Job completed successfully: id={}", job.getId());

        } catch (Exception e) {
            log.error("Job execution failed: id={}, type={}, error={}", 
                job.getId(), job.getJobType(), e.getMessage(), e);
            
            retryOrFailJob(job, e);
            
        } finally {
            sample.stop(executionTimer);
            JobContextHolder.clear();
            semaphore.release();
        }
    }

    /**
     * Marks a job as successful.
     */
    @Transactional
    protected void markAsSuccessful(JobEntity job) {
        try {
            JobEntity current = jobRepository.findById(job.getId()).orElseThrow();
            current.markAsSuccess();
            jobRepository.save(current);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Version conflict - job may have been modified by zombie reaper
            log.warn("Version conflict when marking job as success: id={}", job.getId());
            throw e;
        }
    }

    /**
     * Handles job failure with retry logic and saga compensation.
     */
    @Transactional
    protected void retryOrFailJob(JobEntity job, Exception error) {
        try {
            JobEntity current = jobRepository.findById(job.getId()).orElseThrow();
            
            if (current.getAttempts() >= properties.getMaxAttempts()) {
                // Permanently failed
                current.markAsPermanentlyFailed(error.getMessage());
                permanentFailureCounter.increment();
                
                log.warn("Job permanently failed after {} attempts: id={}", 
                    current.getAttempts(), current.getId());
                
                // Handle saga compensation if applicable
                enqueueCompensationIfNeeded(current);
                
            } else {
                // Temporary failure - schedule for retry with exponential backoff
                current.markAsFailed(error.getMessage());
                current.setStatus(JobStatus.QUEUED);
                
                // Exponential backoff: 2^attempts minutes
                int delayMinutes = (int) Math.pow(2, current.getAttempts());
                current.setRunAt(LocalDateTime.now().plusMinutes(delayMinutes));
                
                failureCounter.increment();
                
                log.info("Job will be retried in {} minutes: id={}, attempt={}", 
                    delayMinutes, current.getId(), current.getAttempts());
            }
            
            jobRepository.save(current);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Version conflict - retry the failure handling
            log.warn("Version conflict during failure handling for job: id={}", job.getId());
            throw e;
        }
    }

    /**
     * Enqueues compensation job if the failed job has one defined.
     */
    private void enqueueCompensationIfNeeded(JobEntity failedJob) {
        try {
            // Deserialize the job to check for compensation
            Class<?> jobClass = Class.forName(failedJob.getJobType());
            com.fasterxml.jackson.databind.ObjectMapper mapper = 
                new com.fasterxml.jackson.databind.ObjectMapper();
            io.github.thestacktracewhisperer.jobs.common.model.Job job = 
                (io.github.thestacktracewhisperer.jobs.common.model.Job) 
                mapper.readValue(failedJob.getPayload(), jobClass);
            
            // Check if compensation is needed
            io.github.thestacktracewhisperer.jobs.common.model.Job compensation = 
                job.getCompensatingJob();
            
            if (compensation != null) {
                log.info("Enqueueing compensation for failed job: id={}", failedJob.getId());
                jobEnqueuer.enqueue(compensation);
                log.info("Compensation job enqueued for: id={}", failedJob.getId());
            }
        } catch (Exception e) {
            log.error("Failed to enqueue compensation for job: id={}", 
                failedJob.getId(), e);
        }
    }
}
