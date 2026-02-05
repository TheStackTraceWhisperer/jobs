package io.github.thestacktracewhisperer.jobs.common.exception;

/**
 * Base exception for job platform errors.
 */
public class JobPlatformException extends RuntimeException {

    public JobPlatformException(String message) {
        super(message);
    }

    public JobPlatformException(String message, Throwable cause) {
        super(message, cause);
    }
}
