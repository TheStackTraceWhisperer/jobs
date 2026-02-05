package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.FulfillOrderJob;
import io.github.thestacktracewhisperer.jobs.reference.service.InventoryService;
import io.github.thestacktracewhisperer.jobs.worker.handler.SagaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for fulfilling orders.
 * Demonstrates saga pattern with automatic compensation on permanent failure.
 */
@Component
public class FulfillOrderHandler implements SagaHandler<FulfillOrderJob> {

    private static final Logger log = LoggerFactory.getLogger(FulfillOrderHandler.class);

    private final InventoryService inventoryService;

    public FulfillOrderHandler(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void handle(FulfillOrderJob job) throws Exception {
        log.info("Fulfilling order: orderId={}, amount={}", job.orderId(), job.amount());
        
        // This will throw an exception if inventory is not available
        inventoryService.reserveInventory(job.orderId());
        
        log.info("Order fulfilled successfully: orderId={}", job.orderId());
    }
}
