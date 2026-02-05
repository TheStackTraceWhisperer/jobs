package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.model.SagaJob;
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
            // Fetch jobs ready for processing
            List<JobEntity> jobs = fetchJobsForProcessing(availablePermits);
            
            if (jobs.isEmpty()) {
                return;
            }

            log.debug("Found {} jobs ready for processing", jobs.size());

            // Process each job asynchronously
            for (JobEntity job : jobs) {
                if (semaphore.tryAcquire()) {
                    // Submit job to executor service for processing
                    executorService.submit(() -> processJob(job));
                } else {
                    break; // No more permits available
                }
            }
        } catch (Exception e) {
            log.error("Error during job polling", e);
        }
    }

    /**
     * Fetches jobs ready for processing using optimistic locking.
     */
    @Transactional
    protected List<JobEntity> fetchJobsForProcessing(int limit) {
        return jobRepository.findJobsReadyForProcessing(
            JobStatus.QUEUED.name(),
            properties.getQueueName(),
            LocalDateTime.now(),
            limit
        );
    }

    /**
     * Processes a single job.
     */
    private void processJob(JobEntity job) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Mark job as processing
            markJobAsProcessing(job);
            
            // Set job context for parent-child tracking
            JobContextHolder.setCurrentJobId(job.getId());
            
            log.info("Processing job: id={}, type={}, attempt={}", 
                job.getId(), job.getJobType(), job.getAttempts() + 1);

            // Route and execute the job
            routingEngine.route(job.getJobType(), job.getPayload());

            // Mark as successful
            markJobAsSuccess(job);
            successCounter.increment();
            
            log.info("Job completed successfully: id={}", job.getId());

        } catch (Exception e) {
            log.error("Job execution failed: id={}, type={}, error={}", 
                job.getId(), job.getJobType(), e.getMessage(), e);
            
            handleJobFailure(job, e);
            
        } finally {
            sample.stop(executionTimer);
            JobContextHolder.clear();
            semaphore.release();
        }
    }

    /**
     * Marks a job as processing.
     */
    @Transactional
    protected void markJobAsProcessing(JobEntity job) {
        JobEntity entity = jobRepository.findById(job.getId()).orElseThrow();
        entity.incrementAttempts();
        entity.markAsProcessing();
        jobRepository.save(entity);
    }

    /**
     * Marks a job as successful.
     */
    @Transactional
    protected void markJobAsSuccess(JobEntity job) {
        JobEntity entity = jobRepository.findById(job.getId()).orElseThrow();
        entity.markAsSuccess();
        jobRepository.save(entity);
    }

    /**
     * Handles job failure with retry logic.
     */
    @Transactional
    protected void handleJobFailure(JobEntity job, Exception error) {
        JobEntity entity = jobRepository.findById(job.getId()).orElseThrow();
        
        if (entity.getAttempts() >= properties.getMaxAttempts()) {
            // Permanently failed
            entity.markAsPermanentlyFailed(error.getMessage());
            permanentFailureCounter.increment();
            
            log.warn("Job permanently failed after {} attempts: id={}", 
                entity.getAttempts(), entity.getId());
            
            // Handle saga compensation if applicable
            handleSagaCompensation(entity);
            
        } else {
            // Temporary failure - schedule for retry with exponential backoff
            entity.markAsFailed(error.getMessage());
            entity.setStatus(JobStatus.QUEUED);
            
            // Exponential backoff: 2^attempts minutes
            int delayMinutes = (int) Math.pow(2, entity.getAttempts());
            entity.setRunAt(LocalDateTime.now().plusMinutes(delayMinutes));
            
            failureCounter.increment();
            
            log.info("Job will be retried in {} minutes: id={}, attempt={}", 
                delayMinutes, entity.getId(), entity.getAttempts());
        }
        
        jobRepository.save(entity);
    }

    /**
     * Handles saga compensation by enqueuing the compensating job.
     */
    private void handleSagaCompensation(JobEntity failedJob) {
        try {
            // Check if this is a SagaJob
            Class<?> jobClass = Class.forName(failedJob.getJobType());
            if (SagaJob.class.isAssignableFrom(jobClass)) {
                log.info("Handling saga compensation for failed job: id={}", failedJob.getId());
                
                // Deserialize and get compensating job
                com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                SagaJob sagaJob = (SagaJob) mapper.readValue(failedJob.getPayload(), jobClass);
                
                // Enqueue compensating job
                jobEnqueuer.enqueue(sagaJob.getCompensatingJob());
                
                log.info("Compensating job enqueued for failed saga: id={}", failedJob.getId());
            }
        } catch (Exception e) {
            log.error("Failed to enqueue compensating job for saga: id={}", 
                failedJob.getId(), e);
        }
    }
}
