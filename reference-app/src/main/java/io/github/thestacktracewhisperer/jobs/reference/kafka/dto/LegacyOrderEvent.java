package io.github.thestacktracewhisperer.jobs.reference.kafka.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for legacy order events from Kafka.
 * This represents the structure of messages on the "orders.created.v1" topic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegacyOrderEvent {
    private UUID orderId;
    private UUID customerId;
    private BigDecimal amount;
}
