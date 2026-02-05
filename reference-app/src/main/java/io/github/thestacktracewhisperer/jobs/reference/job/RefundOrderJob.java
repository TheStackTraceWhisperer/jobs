package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.SimpleJob;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compensating job for order fulfillment failure.
 */
public record RefundOrderJob(UUID orderId, BigDecimal amount) implements SimpleJob {

    @Override
    public String queueName() {
        return "refunds";
    }
}
