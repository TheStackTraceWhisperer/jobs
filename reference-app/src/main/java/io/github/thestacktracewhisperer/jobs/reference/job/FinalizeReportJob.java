package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

import java.util.UUID;

/**
 * Job to finalize a report after all user reports are complete.
 */
public record FinalizeReportJob(UUID reportId) implements Job {

    @Override
    public String queueName() {
        return "reports";
    }
}
