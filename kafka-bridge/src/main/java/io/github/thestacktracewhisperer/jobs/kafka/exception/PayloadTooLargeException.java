package io.github.thestacktracewhisperer.jobs.kafka.exception;

/**
 * Exception thrown when a message payload exceeds the maximum allowed size.
 * This is a permanent failure that should not be retried.
 */
public class PayloadTooLargeException extends IllegalArgumentException {
    
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
