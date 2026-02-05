package io.github.thestacktracewhisperer.jobs.common.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for JobEntity persistence.
 */
@Repository
public interface JobRepository extends JpaRepository<JobEntity, UUID> {

    /**
     * Find jobs that are ready to be processed.
     * 
     * @param status the job status to filter by
     * @param queueName the queue name to filter by
     * @param runAt the maximum run_at time
     * @param limit the maximum number of jobs to return
     * @return list of jobs ready for processing
     */
    @Query(value = """
        SELECT TOP (:limit) *
        FROM background_jobs
        WHERE status = :status
          AND queue_name = :queueName
          AND run_at <= :runAt
        ORDER BY run_at ASC
        """, nativeQuery = true)
    List<JobEntity> findJobsReadyForProcessing(
        @Param("status") String status,
        @Param("queueName") String queueName,
        @Param("runAt") LocalDateTime runAt,
        @Param("limit") int limit
    );

    /**
     * Find zombie jobs (jobs stuck in PROCESSING status without heartbeat updates).
     * 
     * @param status the job status (should be PROCESSING)
     * @param heartbeatThreshold the threshold before which jobs are considered zombies
     * @return list of zombie jobs
     */
    @Query("""
        SELECT j FROM JobEntity j
        WHERE j.status = :status
          AND (j.lastHeartbeat IS NULL OR j.lastHeartbeat < :heartbeatThreshold)
        """)
    List<JobEntity> findZombieJobs(
        @Param("status") JobStatus status,
        @Param("heartbeatThreshold") LocalDateTime heartbeatThreshold
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
}
