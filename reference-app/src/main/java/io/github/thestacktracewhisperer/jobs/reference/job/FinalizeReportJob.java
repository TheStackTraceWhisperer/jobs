package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.SimpleJob;

import java.util.UUID;

/**
 * Job to finalize a report after all user reports are complete.
 */
public record FinalizeReportJob(UUID reportId) implements SimpleJob {

    @Override
    public String queueName() {
        return "reports";
    }
}
