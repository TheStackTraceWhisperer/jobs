package io.github.thestacktracewhisperer.jobs.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity representing a background job in the database.
 */
@Entity
@Table(name = "background_jobs", indexes = {
    @Index(name = "idx_jobs_polling", columnList = "status,queue_name,priority,run_at,version,attempts"),
    @Index(name = "idx_jobs_heartbeat", columnList = "status,last_heartbeat")
})
@Getter
@Setter
@NoArgsConstructor
public class JobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "queue_name", length = 50, nullable = false)
    private String queueName = "DEFAULT";

    @NotNull
    @Column(name = "job_type", length = 255, nullable = false)
    private String jobType;

    @NotNull
    @Column(name = "payload", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String payload;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;
    
    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @NotNull
    @Column(name = "run_at", nullable = false)
    private Instant runAt = Instant.now();

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    @Column(name = "trace_id")
    private UUID traceId;

    @Column(name = "parent_job_id")
    private UUID parentJobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_error", columnDefinition = "NVARCHAR(MAX)")
    private String lastError;

    public JobEntity(String queueName, String jobType, String payload) {
        this.queueName = queueName;
        this.jobType = jobType;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.runAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (runAt == null) {
            runAt = Instant.now();
        }
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsProcessing() {
        this.status = JobStatus.PROCESSING;
        this.lastHeartbeat = Instant.now();
    }

    public void markAsSuccess() {
        this.status = JobStatus.SUCCESS;
    }

    public void markAsFailed(String error) {
        this.status = JobStatus.FAILED;
        this.lastError = error;
    }

    public void markAsPermanentlyFailed(String error) {
        this.status = JobStatus.PERMANENTLY_FAILED;
        this.lastError = error;
    }
}
