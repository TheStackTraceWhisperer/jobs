package io.github.thestacktracewhisperer.jobs.kafka.exception;

/**
 * Exception thrown when a Kafka broker is unreachable or times out during message sending.
 * This exception triggers the Platform's Retry Policy with exponential backoff.
 */
public class KafkaBrokerException extends RuntimeException {
    
    public KafkaBrokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
