package io.github.thestacktracewhisperer.jobs.common.dto;

/**
 * DTO for queue statistics aggregated from the database.
 * Used for efficient queue depth and age metrics.
 */
public record QueueStats(
    String queueName,
    long queuedCount,
    Long oldestJobAgeSeconds
) {}
