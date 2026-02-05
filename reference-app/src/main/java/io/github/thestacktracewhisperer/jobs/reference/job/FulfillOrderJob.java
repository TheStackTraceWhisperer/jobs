package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Job for order fulfillment.
 */
public record FulfillOrderJob(UUID orderId, BigDecimal amount) implements Job {

    @Override
    public String queueName() {
        return "orders";
    }
}
