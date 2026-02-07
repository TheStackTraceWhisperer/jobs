package io.github.thestacktracewhisperer.jobs.ui.service;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.ui.exception.JobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing jobs via the UI.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    /**
     * Find jobs with optional filtering and paging.
     */
    @Transactional(readOnly = true)
    public Page<JobEntity> findJobs(JobStatus status, String queueName, String jobType, Pageable pageable) {
        Specification<JobEntity> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (queueName != null && !queueName.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("queueName"), queueName));
        }

        if (jobType != null && !jobType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("jobType"), jobType + "%"));
        }

        return jobRepository.findAll(spec, pageable);
    }

    /**
     * Find a job by ID.
     */
    @Transactional(readOnly = true)
    public Optional<JobEntity> findById(UUID id) {
        return jobRepository.findById(id);
    }

    /**
     * Requeue a job (mark as QUEUED with runAt set to now).
     */
    @Transactional
    public void requeue(UUID id) {
        JobEntity job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        
        job.setStatus(JobStatus.QUEUED);
        job.setRunAt(Instant.now());
        jobRepository.save(job);
    }

    /**
     * Cancel a job (mark as CANCELLED).
     */
    @Transactional
    public void cancel(UUID id) {
        JobEntity job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
        
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);
    }

    /**
     * Delete a job permanently.
     * This operation is idempotent - deleting a non-existent job succeeds silently.
     */
    @Transactional
    public void delete(UUID id) {
        jobRepository.deleteById(id);
    }
}
