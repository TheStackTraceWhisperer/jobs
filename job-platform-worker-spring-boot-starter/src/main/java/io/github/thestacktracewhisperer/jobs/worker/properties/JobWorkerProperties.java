package io.github.thestacktracewhisperer.jobs.worker.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the job worker.
 */
@ConfigurationProperties(prefix = "platform.jobs.worker")
public class JobWorkerProperties {

    /**
     * Whether the worker is enabled.
     */
    private boolean enabled = false;

    /**
     * Maximum number of concurrent jobs.
     */
    private int concurrency = 10;

    /**
     * Queue name to poll from.
     */
    private String queueName = "DEFAULT";

    /**
     * Maximum number of retry attempts before marking a job as permanently failed.
     */
    private int maxAttempts = 3;

    /**
     * Zombie job threshold in minutes (jobs without heartbeat updates).
     */
    private int zombieThresholdMinutes = 5;

    /**
     * Polling interval in milliseconds.
     */
    private long pollingIntervalMs = 1000;

    /**
     * Reaper interval in milliseconds.
     */
    private long reaperIntervalMs = 60000;

    /**
     * Shutdown timeout in seconds (time to wait for running jobs to complete).
     */
    private long shutdownTimeoutSeconds = 30;

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getZombieThresholdMinutes() {
        return zombieThresholdMinutes;
    }

    public void setZombieThresholdMinutes(int zombieThresholdMinutes) {
        this.zombieThresholdMinutes = zombieThresholdMinutes;
    }

    public long getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public void setPollingIntervalMs(long pollingIntervalMs) {
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public long getReaperIntervalMs() {
        return reaperIntervalMs;
    }

    public void setReaperIntervalMs(long reaperIntervalMs) {
        this.reaperIntervalMs = reaperIntervalMs;
    }

    public long getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }

    public void setShutdownTimeoutSeconds(long shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }
}
