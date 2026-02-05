package io.github.thestacktracewhisperer.jobs.common.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity representing a background job in the database.
 */
@Entity
@Table(name = "background_jobs", indexes = {
    @Index(name = "idx_jobs_polling", columnList = "status,queue_name,run_at,version,attempts"),
    @Index(name = "idx_jobs_heartbeat", columnList = "status,last_heartbeat")
})
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

    @NotNull
    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt = LocalDateTime.now();

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Version
    @Column(name = "version", nullable = false)
    private int version = 0;

    @Column(name = "trace_id")
    private UUID traceId;

    @Column(name = "parent_job_id")
    private UUID parentJobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_error", columnDefinition = "NVARCHAR(MAX)")
    private String lastError;

    // Constructors
    public JobEntity() {
    }

    public JobEntity(String queueName, String jobType, String payload) {
        this.queueName = queueName;
        this.jobType = jobType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
        this.runAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getRunAt() {
        return runAt;
    }

    public void setRunAt(LocalDateTime runAt) {
        this.runAt = runAt;
    }

    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public UUID getTraceId() {
        return traceId;
    }

    public void setTraceId(UUID traceId) {
        this.traceId = traceId;
    }

    public UUID getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId(UUID parentJobId) {
        this.parentJobId = parentJobId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (runAt == null) {
            runAt = LocalDateTime.now();
        }
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsProcessing() {
        this.status = JobStatus.PROCESSING;
        this.lastHeartbeat = LocalDateTime.now();
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
