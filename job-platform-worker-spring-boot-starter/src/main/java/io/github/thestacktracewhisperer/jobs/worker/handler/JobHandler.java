package io.github.thestacktracewhisperer.jobs.worker.handler;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

/**
 * Handler interface for processing jobs.
 * Implementations should be registered as Spring beans and will be automatically discovered.
 *
 * @param <T> the type of job this handler processes
 */
public interface JobHandler<T extends Job> {

    /**
     * Handles the execution of a job.
     * 
     * @param job the job to handle
     * @throws Exception if job execution fails
     */
    void handle(T job) throws Exception;
}
