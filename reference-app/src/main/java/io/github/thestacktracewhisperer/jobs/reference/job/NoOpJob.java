package io.github.thestacktracewhisperer.jobs.reference.job;

/**
 * A no-op job for performance testing.
 * This job does nothing except return immediately, allowing us to measure
 * the throughput and drain rate of the job processing system without
 * any actual work being performed.
 *
 * @param jobId Unique identifier for tracking
 * @param shouldFail If true, the handler will throw an exception
 */
public record NoOpJob(String jobId, boolean shouldFail) {
    /**
     * Constructor for a successful no-op job.
     */
    public NoOpJob(String jobId) {
        this(jobId, false);
    }
}
