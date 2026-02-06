package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Service responsible for atomic job claiming and status updates.
 * Separated from BackgroundWorker to ensure proper transaction propagation through Spring proxy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobClaimService {

    private final JobRepository jobRepository;

    /**
     * Atomically fetches and claims jobs within a single transaction.
     * Uses pessimistic locking to prevent concurrent claims.
     */
    @Transactional
    public List<JobEntity> fetchAndClaimJobs(
            Collection<String> supportedTypes,
            String queueName,
            int batchSize) {
        
        if (supportedTypes.isEmpty()) {
            log.warn("No supported job types provided, skipping fetch");
            return Collections.emptyList();
        }
        
        org.springframework.data.domain.Pageable pageRequest = 
            org.springframework.data.domain.PageRequest.of(0, batchSize);
            
        org.springframework.data.domain.Page<JobEntity> candidatePage = 
            jobRepository.findJobsReadyForProcessing(
                JobStatus.QUEUED,
                queueName,
                Instant.now(),
                supportedTypes,
                pageRequest
            );
        
        List<JobEntity> candidates = candidatePage.getContent();
        List<JobEntity> claimed = new ArrayList<>();
        
        for (JobEntity candidate : candidates) {
            try {
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
     * Updates job status to SUCCESS within a transaction.
     */
    @Transactional
    public void markJobSuccess(java.util.UUID jobId) {
        try {
            JobEntity current = jobRepository.findById(jobId).orElseThrow(
                () -> new IllegalStateException("Job not found: " + jobId));
            current.markAsSuccess();
            jobRepository.save(current);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict when marking job as success: id={}", jobId);
            throw e;
        }
    }

    /**
     * Handles job failure with retry or permanent failure within a transaction.
     */
    @Transactional
    public void handleJobFailure(java.util.UUID jobId, String errorMessage, int maxAttempts) {
        try {
            JobEntity current = jobRepository.findById(jobId).orElseThrow(
                () -> new IllegalStateException("Job not found: " + jobId));
            
            if (current.getAttempts() >= maxAttempts) {
                current.markAsPermanentlyFailed(errorMessage);
                log.warn("Job permanently failed after {} attempts: id={}", 
                    current.getAttempts(), current.getId());
            } else {
                current.markAsFailed(errorMessage);
                current.setStatus(JobStatus.QUEUED);
                
                int delayMinutes = (int) Math.pow(2, current.getAttempts());
                current.setRunAt(Instant.now().plus(Duration.ofMinutes(delayMinutes)));
                
                log.info("Job will be retried in {} minutes: id={}, attempt={}", 
                    delayMinutes, current.getId(), current.getAttempts());
            }
            
            jobRepository.save(current);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict during failure handling for job: id={}", jobId);
            throw e;
        }
    }

    /**
     * Handles job snooze by resetting status to QUEUED and refunding the attempt counter.
     */
    @Transactional
    public void handleJobSnooze(java.util.UUID jobId, JobSnoozeException snooze) {
        try {
            JobEntity entity = jobRepository.findById(jobId).orElseThrow(
                () -> new IllegalStateException("Job not found: " + jobId));
            
            // 1. Reset Status
            entity.setStatus(JobStatus.QUEUED);
            
            // 2. Schedule Future Run
            entity.setRunAt(Instant.now().plus(snooze.getDelay()));
            
            // 3. Refund the Attempt
            // We subtract 1 because fetchAndClaimJobs() added 1. 
            // This ensures the net change to 'attempts' is 0.
            int adjustedAttempts = Math.max(0, entity.getAttempts() - 1);
            entity.setAttempts(adjustedAttempts);
            
            // 4. Update Audit Log (Optional but recommended)
            entity.setLastError("Snoozed: " + snooze.getMessage());
            
            jobRepository.save(entity);
            
            log.info("Job snoozed: id={}, delay={}, message={}", 
                entity.getId(), snooze.getDelay(), snooze.getMessage());
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            log.warn("Version conflict during snooze handling for job: id={}", jobId);
            throw e;
        }
    }
}
