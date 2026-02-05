package io.github.thestacktracewhisperer.jobs.common.exception;

/**
 * Exception thrown when job serialization or deserialization fails.
 */
public class JobSerializationException extends JobPlatformException {

    public JobSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
