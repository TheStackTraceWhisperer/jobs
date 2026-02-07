package io.github.thestacktracewhisperer.jobs.reference.kafka.ingestor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.reference.kafka.dto.LegacyOrderEvent;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.reference.job.FulfillOrderJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Ingestor for legacy order events from Kafka.
 * 
 * <p>This component replaces complex Kafka Consumers with a "Dumb Ingestor" that simply
 * deserializes the Kafka message and enqueues a Domain Job. The actual processing logic
 * has moved to the JobHandler.</p>
 * 
 * <p><b>Manual Ack Mode:</b> Manual acknowledgment is REQUIRED to ensure we don't commit
 * offset until DB insert succeeds. If enqueue throws, acknowledgment is skipped and
 * Kafka will redeliver the message.</p>
 * 
 * <p><b>Poison Pill Strategy:</b> If deserialization fails (e.g., bad JSON), retrying
 * won't fix the problem. In this case, we log an error, alert, and acknowledge to
 * unblock the partition.</p>
 * 
 * <p><b>Risk:</b> Duplicate delivery is possible if DB commits but network fails before
 * ack reaches the broker. The FulfillOrderHandler must be written to handle duplicates
 * (e.g., "If order status is already FULFILLING, do nothing").</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyOrderIngestor {

    private final JobEnqueuer jobEnqueuer;
    private final ObjectMapper objectMapper;

    /**
     * Listens to legacy order events and converts them to domain jobs.
     * Uses manual acknowledgment mode to ensure transactional safety.
     */
    @KafkaListener(
        topics = "orders.created.v1", 
        groupId = "job-platform-bridge",
        containerFactory = "kafkaManualAckListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        try {
            log.info("Received message from Kafka: topic={}, partition={}, offset={}", 
                record.topic(), record.partition(), record.offset());
            
            // 1. Deserialization (Fail Fast if schema is invalid)
            LegacyOrderEvent event = objectMapper.readValue(record.value(), LegacyOrderEvent.class);

            // 2. Transformation
            FulfillOrderJob job = new FulfillOrderJob(
                event.getOrderId(), 
                event.getAmount()
            );

            // 3. Persistence
            jobEnqueuer.enqueue(job);

            // 4. Commit Offset
            // If enqueue throws, this line is skipped, Kafka redelivers.
            ack.acknowledge();
            
            log.info("Successfully ingested order event: orderId={}, jobId created", event.getOrderId());

        } catch (JsonProcessingException e) {
            // POISON PILL STRATEGY
            // We cannot process this message. Retrying won't fix bad JSON.
            // We must Log, Alert, and Acknowledge to unblock the partition.
            log.error("POISON PILL: Failed to deserialize message at offset {} - ACKNOWLEDGING to unblock partition", 
                record.offset(), e);
            ack.acknowledge();
            
        } catch (Exception e) {
            // Transient DB failure? Throw to let Kafka retry.
            log.error("Transient failure ingesting message at offset {} - will retry", 
                record.offset(), e);
            throw e;
            
        } finally {
            MDC.clear();
        }
    }
}
