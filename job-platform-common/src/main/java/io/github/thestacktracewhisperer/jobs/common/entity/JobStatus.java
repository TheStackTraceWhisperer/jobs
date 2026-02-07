package io.github.thestacktracewhisperer.jobs.common.entity;

/**
 * Enumeration of possible job statuses.
 */
public enum JobStatus {
    /**
     * Job is queued and waiting to be processed.
     */
    QUEUED,
    
    /**
     * Job is currently being processed by a worker.
     */
    PROCESSING,
    
    /**
     * Job completed successfully.
     */
    SUCCESS,
    
    /**
     * Job failed but may be retried.
     */
    FAILED,
    
    /**
     * Job permanently failed after exhausting all retries.
     */
    PERMANENTLY_FAILED,
    
    /**
     * Job was cancelled and will not be processed.
     */
    CANCELLED
}
