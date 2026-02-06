package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.FulfillOrderJob;
import io.github.thestacktracewhisperer.jobs.reference.service.InventoryService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for fulfilling orders.
 * Demonstrates saga pattern with automatic compensation on permanent failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FulfillOrderHandler implements JobHandler<FulfillOrderJob> {

    private final InventoryService inventoryService;

    @Override
    public void handle(FulfillOrderJob job) throws Exception {
        log.info("Fulfilling order: orderId={}, amount={}", job.orderId(), job.amount());
        
        // This will throw an exception if inventory is not available
        inventoryService.reserveInventory(job.orderId());
        
        log.info("Order fulfilled successfully: orderId={}", job.orderId());
    }
}
