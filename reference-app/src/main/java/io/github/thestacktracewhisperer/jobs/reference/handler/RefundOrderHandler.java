package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.RefundOrderJob;
import io.github.thestacktracewhisperer.jobs.reference.service.PaymentService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for processing refunds (compensating transaction).
 */
@Component
@RequiredArgsConstructor
public class RefundOrderHandler implements JobHandler<RefundOrderJob> {

    private static final Logger log = LoggerFactory.getLogger(RefundOrderHandler.class);

    private final PaymentService paymentService;

    @Override
    public void handle(RefundOrderJob job) throws Exception {
        log.info("Processing refund: orderId={}, amount={}", job.orderId(), job.amount());
        
        paymentService.processRefund(job.orderId(), job.amount());
        
        log.info("Refund processed successfully: orderId={}", job.orderId());
    }
}
