package io.github.thestacktracewhisperer.jobs.common.model;

/**
 * Interface for jobs that implement the Saga pattern.
 * If a saga job fails after exhausting retries, the compensating job will be automatically queued.
 */
public non-sealed interface SagaJob extends Job {
    
    /**
     * Returns the compensating job to execute if this saga job permanently fails.
     * @return the compensating job
     */
    Job getCompensatingJob();
}
