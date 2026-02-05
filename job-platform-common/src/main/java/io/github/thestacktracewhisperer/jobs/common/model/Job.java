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
    
    /**
     * Returns the compensating job to execute if this job permanently fails.
     * This enables the Saga pattern for automatic compensation.
     * @return the compensating job, or null if no compensation is needed
     */
    default Job getCompensatingJob() {
        return null;
    }
}
