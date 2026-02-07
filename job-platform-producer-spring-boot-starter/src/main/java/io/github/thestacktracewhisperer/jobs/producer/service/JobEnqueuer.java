package io.github.thestacktracewhisperer.jobs.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSerializationException;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for enqueueing jobs to the background job system.
 * Provides transactional job persistence with parent-child tracking and MDC trace ID capture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobEnqueuer {
    private static final String MDC_TRACE_ID_KEY = "traceId";

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final JobMetricsService metricsService;

    /**
     * Enqueues a job for immediate execution.
     * 
     * @param job the job to enqueue
     * @return the persisted job entity
     */
    @Transactional
    public JobEntity enqueue(Job job) {
        return enqueue(job, null);
    }

    /**
     * Enqueues a job to run at a specific time.
     * 
     * @param job the job to enqueue
     * @param runAt the time when the job should run (null for immediate)
     * @return the persisted job entity
     */
    @Transactional
    public JobEntity enqueue(Job job, Instant runAt) {
        Instant enqueueStart = Instant.now();
        String jobType = job.getClass().getName();
        
        try {
            // Serialize job to JSON
            String payload = objectMapper.writeValueAsString(job);
            String queueName = job.queueName();

            // Create job entity
            JobEntity entity = new JobEntity(queueName, jobType, payload);
            
            // Set priority from job
            entity.setPriority(job.priority());
            
            if (runAt != null) {
                entity.setRunAt(runAt);
            }

            // Capture MDC trace ID if available
            String traceId = MDC.get(MDC_TRACE_ID_KEY);
            if (traceId != null) {
                try {
                    entity.setTraceId(UUID.fromString(traceId));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid trace ID format in MDC: {}", traceId);
                }
            }

            // Set parent job ID if running within another job
            if (JobContextHolder.hasCurrentJobId()) {
                entity.setParentJobId(JobContextHolder.getCurrentJobId());
            }

            // Persist and return
            JobEntity saved = jobRepository.save(entity);
            
            // Record metrics - wrap in try-catch to prevent transaction rollback on observability failures
            try {
                Duration enqueueDuration = Duration.between(enqueueStart, Instant.now());
                metricsService.recordEnqueueTime(jobType, enqueueDuration);
                metricsService.recordJobEnqueued(jobType, queueName);
            } catch (Exception e) {
                // Suppress observability failures to protect the business transaction
                log.warn("Failed to record metrics for job {}", saved.getId(), e);
            }
            
            log.info("Enqueued job: id={}, type={}, queue={}", 
                saved.getId(), jobType, queueName);
            
            return saved;

        } catch (Exception e) {
            throw new JobSerializationException(
                "Failed to serialize job: " + jobType, e);
        }
    }
    
    /**
     * Enqueues multiple jobs in a single transaction.
     * All jobs will share the same parent job ID and trace ID context.
     * 
     * @param jobs the list of jobs to enqueue
     * @return the list of persisted job entities
     */
    @Transactional
    public List<JobEntity> enqueue(List<? extends Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return new ArrayList<>();
        }
        
        Instant enqueueStart = Instant.now();
        
        // Capture context once for the entire batch
        String traceId = MDC.get(MDC_TRACE_ID_KEY);
        UUID traceIdUuid = null;
        if (traceId != null) {
            try {
                traceIdUuid = UUID.fromString(traceId);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid trace ID format in MDC: {}", traceId);
            }
        }
        
        UUID parentJobId = JobContextHolder.hasCurrentJobId() 
            ? JobContextHolder.getCurrentJobId() 
            : null;
        
        List<JobEntity> entities = new ArrayList<>(jobs.size());
        
        try {
            for (Job job : jobs) {
                String jobType = job.getClass().getName();
                String payload = objectMapper.writeValueAsString(job);
                String queueName = job.queueName();
                
                JobEntity entity = new JobEntity(queueName, jobType, payload);
                entity.setPriority(job.priority());
                
                // Apply shared context to every entity
                if (traceIdUuid != null) {
                    entity.setTraceId(traceIdUuid);
                }
                if (parentJobId != null) {
                    entity.setParentJobId(parentJobId);
                }
                
                entities.add(entity);
            }
            
            // Save all entities in one batch
            List<JobEntity> savedEntities = jobRepository.saveAll(entities);
            
            // Record metrics - wrap in try-catch to prevent transaction rollback
            try {
                Duration enqueueDuration = Duration.between(enqueueStart, Instant.now());
                
                // Record individual job metrics
                for (JobEntity entity : savedEntities) {
                    metricsService.recordEnqueueTime(entity.getJobType(), enqueueDuration);
                    metricsService.recordJobEnqueued(entity.getJobType(), entity.getQueueName());
                }
                
                // Record batch size metric
                metricsService.recordBatchSize(savedEntities.size());
            } catch (Exception e) {
                log.warn("Failed to record metrics for batch enqueue", e);
            }
            
            log.info("Enqueued {} jobs in batch", savedEntities.size());
            
            return savedEntities;
            
        } catch (Exception e) {
            throw new JobSerializationException(
                "Failed to serialize jobs in batch", e);
        }
    }
}
