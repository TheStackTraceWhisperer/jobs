package io.github.thestacktracewhisperer.jobs.worker.handler;

import io.github.thestacktracewhisperer.jobs.common.model.SagaJob;

/**
 * Handler interface for saga jobs with compensation logic.
 *
 * @param <T> the type of saga job this handler processes
 */
public interface SagaHandler<T extends SagaJob> extends JobHandler<T> {

    /**
     * Optional undo operation for saga compensation.
     * By default, the system will automatically enqueue the compensating job
     * returned by {@link SagaJob#getCompensatingJob()}.
     * 
     * @param job the job to undo
     */
    default void undo(T job) {
        // Optional override
    }
}
