package io.github.thestacktracewhisperer.jobs.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSerializationException;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for enqueueing jobs to the background job system.
 * Provides transactional job persistence with parent-child tracking and MDC trace ID capture.
 */
@Service
@RequiredArgsConstructor
public class JobEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(JobEnqueuer.class);
    private static final String MDC_TRACE_ID_KEY = "traceId";

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;

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
    public JobEntity enqueue(Job job, LocalDateTime runAt) {
        try {
            // Serialize job to JSON
            String payload = objectMapper.writeValueAsString(job);
            String jobType = job.getClass().getName();
            String queueName = job.queueName();

            // Create job entity
            JobEntity entity = new JobEntity(queueName, jobType, payload);
            
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
            log.info("Enqueued job: id={}, type={}, queue={}", 
                saved.getId(), jobType, queueName);
            
            return saved;

        } catch (Exception e) {
            throw new JobSerializationException(
                "Failed to serialize job: " + job.getClass().getName(), e);
        }
    }
}
