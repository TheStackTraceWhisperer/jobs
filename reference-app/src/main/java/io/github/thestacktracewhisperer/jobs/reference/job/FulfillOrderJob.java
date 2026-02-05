package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.common.model.SagaJob;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Example Saga job for order fulfillment.
 * If this job fails permanently, a refund job will be automatically enqueued.
 */
public record FulfillOrderJob(UUID orderId, BigDecimal amount) implements SagaJob {

    @Override
    public Job getCompensatingJob() {
        return new RefundOrderJob(orderId, amount);
    }

    @Override
    public String queueName() {
        return "orders";
    }
}
