package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

import java.util.UUID;

/**
 * Child job for generating a report for a single user.
 */
public record UserReportJob(UUID reportId, UUID userId) implements Job {

    @Override
    public String queueName() {
        return "reports";
    }
}
