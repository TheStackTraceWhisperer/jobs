package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.FanOutJob;

import java.util.UUID;

/**
 * Fan-out job that generates monthly reports for all active users.
 */
public record GenerateMonthlyReportJob(UUID reportId, int year, int month) implements FanOutJob {

    @Override
    public String queueName() {
        return "reports";
    }
}
