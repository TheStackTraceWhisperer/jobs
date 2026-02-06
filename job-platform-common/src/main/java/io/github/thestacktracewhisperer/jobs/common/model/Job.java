package io.github.thestacktracewhisperer.jobs.common.model;

/**
 * Interface representing a background job.
 * All job implementations should implement this interface.
 */
public interface Job {
    
    /**
     * Returns the queue name this job should be processed in.
     * @return the queue name, defaults to "DEFAULT"
     */
    default String queueName() {
        return "DEFAULT";
    }
}
