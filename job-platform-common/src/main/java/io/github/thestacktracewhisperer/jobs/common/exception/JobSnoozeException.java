package io.github.thestacktracewhisperer.jobs.common.exception;

import java.time.Duration;

/**
 * Thrown by a JobHandler when it wants to yield execution and try again later.
 * This does NOT count as a failure attempt.
 */
public class JobSnoozeException extends RuntimeException {
    
    private final Duration delay;

    public JobSnoozeException(String message, Duration delay) {
        super(message);
        this.delay = delay;
    }

    public Duration getDelay() {
        return delay;
    }
}
