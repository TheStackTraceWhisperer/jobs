package io.github.thestacktracewhisperer.jobs.producer.context;

import java.util.UUID;

/**
 * Thread-local context holder for tracking the current job execution context.
 * Used to establish parent-child relationships between jobs.
 */
public final class JobContextHolder {

    private static final ThreadLocal<UUID> currentJobId = new ThreadLocal<>();

    private JobContextHolder() {
        // Utility class
    }

    /**
     * Sets the current job ID in the thread-local context.
     * 
     * @param jobId the job ID to set
     */
    public static void setCurrentJobId(UUID jobId) {
        currentJobId.set(jobId);
    }

    /**
     * Gets the current job ID from the thread-local context.
     * 
     * @return the current job ID, or null if not set
     */
    public static UUID getCurrentJobId() {
        return currentJobId.get();
    }

    /**
     * Clears the current job ID from the thread-local context.
     */
    public static void clear() {
        currentJobId.remove();
    }

    /**
     * Checks if there is a current job ID set.
     * 
     * @return true if a job ID is set, false otherwise
     */
    public static boolean hasCurrentJobId() {
        return currentJobId.get() != null;
    }
}
