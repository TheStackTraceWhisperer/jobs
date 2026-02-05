package io.github.thestacktracewhisperer.jobs.reference.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock payment service for demonstration.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public void processRefund(UUID orderId, BigDecimal amount) {
        log.info("Processing refund for order: {}, amount: {}", orderId, amount);
        // In a real implementation, this would interact with a payment gateway
    }
}
