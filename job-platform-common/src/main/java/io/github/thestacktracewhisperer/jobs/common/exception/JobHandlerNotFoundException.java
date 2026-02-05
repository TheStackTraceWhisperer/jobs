package io.github.thestacktracewhisperer.jobs.common.exception;

/**
 * Exception thrown when a job handler cannot be found for a job type.
 */
public class JobHandlerNotFoundException extends JobPlatformException {

    public JobHandlerNotFoundException(String jobType) {
        super("No handler found for job type: " + jobType);
    }
}
