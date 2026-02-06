package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.RefundOrderJob;
import io.github.thestacktracewhisperer.jobs.reference.service.PaymentService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for processing refunds (compensating transaction).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundOrderHandler implements JobHandler<RefundOrderJob> {

    private final PaymentService paymentService;

    @Override
    public void handle(RefundOrderJob job) throws Exception {
        log.info("Processing refund: orderId={}, amount={}", job.orderId(), job.amount());
        
        paymentService.processRefund(job.orderId(), job.amount());
        
        log.info("Refund processed successfully: orderId={}", job.orderId());
    }
}
