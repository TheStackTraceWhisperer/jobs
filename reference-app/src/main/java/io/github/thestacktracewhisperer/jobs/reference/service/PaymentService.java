package io.github.thestacktracewhisperer.jobs.reference.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mock payment service for demonstration.
 */
@Service
@Slf4j
public class PaymentService {

    public void processRefund(UUID orderId, BigDecimal amount) {
        log.info("Processing refund for order: {}, amount: {}", orderId, amount);
        // In a real implementation, this would interact with a payment gateway
    }
}
