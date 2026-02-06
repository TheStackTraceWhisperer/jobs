package io.github.thestacktracewhisperer.jobs.common.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for JobEntity persistence.
 */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    /**
     * Find jobs ready for processing with pessimistic locking.
     * Filters by queue, status, scheduled time and supported job types.
     * 
     * @param status the job status to filter by
     * @param queueName the queue name to filter by
     * @param runAt the maximum run_at time
     * @param supportedTypes list of job type class names this worker can process
     * @param pageable pagination settings for batch size
     * @return page of jobs ready for processing with exclusive locks
     */
    @Query("""
        SELECT j FROM JobEntity j
        WHERE j.status = :status
          AND j.queueName = :queueName
          AND j.runAt <= :runAt
          AND j.jobType IN :supportedTypes
        ORDER BY j.runAt ASC
        """)
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    org.springframework.data.domain.Page<JobEntity> findJobsReadyForProcessing(
        @Param("status") JobStatus status,
        @Param("queueName") String queueName,
        @Param("runAt") Instant runAt,
        @Param("supportedTypes") java.util.Collection<String> supportedTypes,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Find zombie jobs (jobs stuck in PROCESSING status without heartbeat updates).
     * Uses pessimistic lock to prevent race with active workers.
     * 
     * @param status the job status (should be PROCESSING)
     * @param heartbeatThreshold the threshold before which jobs are considered zombies
     * @return list of zombie jobs with exclusive locks
     */
    @Query("""
        SELECT j FROM JobEntity j
        WHERE j.status = :status
          AND (j.lastHeartbeat IS NULL OR j.lastHeartbeat < :heartbeatThreshold)
        """)
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<JobEntity> findZombieJobs(
        @Param("status") JobStatus status,
        @Param("heartbeatThreshold") Instant heartbeatThreshold
    );

    /**
     * Count jobs by status and queue.
     * 
     * @param status the job status
     * @param queueName the queue name
     * @return count of jobs
     */
    long countByStatusAndQueueName(JobStatus status, String queueName);

    /**
     * Count jobs by status.
     * 
     * @param status the job status
     * @return count of jobs
     */
    long countByStatus(JobStatus status);

    /**
     * Returns count of QUEUED jobs and creation time of the oldest job per queue.
     * Uses JPQL for better database portability.
     *
     * <p>The third column in the result is the {@code Instant} representing
     * the earliest {@code createdAt} timestamp among QUEUED jobs for the queue.
     * Consumers can compute the age in seconds in application code if needed.</p>
     *
     * @return list of queue statistics as Object[]:
     *         [0] = queue name (String),
     *         [1] = queued count (Long),
     *         [2] = oldest job createdAt (Instant)
     */
    @Query("""
        SELECT j.queueName AS queueName,
               COUNT(j.queueName) AS queuedCount,
               MIN(j.createdAt) AS oldestJobCreatedAt
        FROM JobEntity j
        WHERE j.status = io.github.thestacktracewhisperer.jobs.common.entity.JobStatus.QUEUED
        GROUP BY j.queueName
        """)
    List<Object[]> getQueueStatisticsNative();

    /**
     * Updates the heartbeat timestamp for a running job.
     * Used by workers to signal they are still processing the job.
     * 
     * @param id the job ID
     * @param heartbeat the heartbeat timestamp
     */
    @Modifying
    @Query("UPDATE JobEntity j SET j.lastHeartbeat = :heartbeat WHERE j.id = :id")
    void updateLastHeartbeat(@Param("id") UUID id, @Param("heartbeat") Instant heartbeat);
}
