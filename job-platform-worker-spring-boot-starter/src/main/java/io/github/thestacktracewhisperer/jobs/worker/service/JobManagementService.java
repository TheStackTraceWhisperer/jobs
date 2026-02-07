package io.github.thestacktracewhisperer.jobs.worker.service;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for administrative job management operations.
 * Provides job cancellation and retry functionality.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobManagementService {

    private final JobRepository jobRepository;

    /**
     * Cancels a job by setting its status to CANCELLED.
     * Jobs in terminal states (SUCCESS, PERMANENTLY_FAILED, CANCELLED) cannot be cancelled.
     * Jobs in PROCESSING state can be cancelled - the worker will fail to update status due to optimistic locking.
     * 
     * @param jobId the ID of the job to cancel
     * @throws IllegalArgumentException if the job doesn't exist
     * @throws IllegalStateException if the job is in a terminal state
     */
    @Transactional
    public void cancelJob(UUID jobId) {
        JobEntity job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // Guard: Cannot cancel terminal states
        if (job.getStatus() == JobStatus.SUCCESS 
            || job.getStatus() == JobStatus.PERMANENTLY_FAILED 
            || job.getStatus() == JobStatus.CANCELLED) {
            throw new IllegalStateException(
                "Cannot cancel job in terminal state: " + job.getStatus());
        }
        
        // Cancel the job
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
        
        log.info("Cancelled job: id={}, previousStatus={}", jobId, job.getStatus());
    }

    /**
     * Retries a permanently failed job by resetting it to QUEUED state.
     * Only jobs in PERMANENTLY_FAILED state can be retried.
     * 
     * @param jobId the ID of the job to retry
     * @throws IllegalArgumentException if the job doesn't exist
     * @throws IllegalStateException if the job is not in PERMANENTLY_FAILED state
     */
    @Transactional
    public void retryJob(UUID jobId) {
        JobEntity job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        
        // Guard: Only PERMANENTLY_FAILED jobs can be retried
        if (job.getStatus() != JobStatus.PERMANENTLY_FAILED) {
            throw new IllegalStateException(
                "Can only retry jobs in PERMANENTLY_FAILED state, current state: " + job.getStatus());
        }
        
        // Reset job for retry
        job.setStatus(JobStatus.QUEUED);
        job.setAttempts(0);
        job.setRunAt(Instant.now());
        
        // Append to error log
        String currentError = job.getLastError();
        if (currentError != null && !currentError.isEmpty()) {
            job.setLastError(currentError + " | Manually retried");
        } else {
            job.setLastError("Manually retried");
        }
        
        jobRepository.save(job);
        
        log.info("Retried job: id={}, resetAttempts=0, runAt={}", jobId, job.getRunAt());
    }
}
