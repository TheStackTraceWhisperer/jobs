package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

import java.util.UUID;

/**
 * Fan-out job that generates monthly reports for all active users.
 */
public record GenerateMonthlyReportJob(UUID reportId, int year, int month) implements Job {

    @Override
    public String queueName() {
        return "reports";
    }
}
