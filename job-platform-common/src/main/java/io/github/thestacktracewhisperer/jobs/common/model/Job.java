package io.github.thestacktracewhisperer.jobs.common.model;

/**
 * Sealed interface representing a background job.
 * All job implementations must extend one of the permitted subtypes.
 */
public sealed interface Job permits SimpleJob, SagaJob, FanOutJob {
    
    /**
     * Returns the queue name this job should be processed in.
     * @return the queue name, defaults to "DEFAULT"
     */
    default String queueName() {
        return "DEFAULT";
    }
}
