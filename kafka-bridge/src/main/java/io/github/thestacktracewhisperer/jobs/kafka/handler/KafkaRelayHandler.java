package io.github.thestacktracewhisperer.jobs.kafka.handler;

import io.github.thestacktracewhisperer.jobs.kafka.exception.KafkaBrokerException;
import io.github.thestacktracewhisperer.jobs.kafka.exception.PayloadTooLargeException;
import io.github.thestacktracewhisperer.jobs.kafka.properties.KafkaRelayProperties;
import io.github.thestacktracewhisperer.jobs.shared.kafka.KafkaRelayJob;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handler for relaying messages to Kafka.
 * 
 * <p>This handler ensures messages are delivered to the broker before marking the job as SUCCESS.
 * It uses synchronous sending to guarantee delivery - if we fire-and-forget, the job might succeed
 * but the broker could reject the message, leading to data loss.</p>
 * 
 * <p>If sending fails due to broker unavailability or timeout, the handler throws a
 * {@link KafkaBrokerException} which triggers the Platform's Retry Policy with exponential backoff.</p>
 * 
 * <p><b>Risk Mitigation:</b> To prevent the "Retry Storm" amplification issue, the handler
 * checks payload size before sending. If the payload exceeds Kafka's message size limit,
 * the handler throws a {@link PayloadTooLargeException} which fails permanently rather than retrying.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaRelayHandler implements JobHandler<KafkaRelayJob> {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaRelayProperties properties;

    @Override
    public void handle(KafkaRelayJob job) throws Exception {
        // Risk Mitigation: Check payload size before attempting to send
        validatePayloadSize(job);
        
        try {
            ProducerRecord<String, String> record = createProducerRecord(job);

            // CRITICAL: Synchronous Send
            // We must wait for the broker ACK. If we fire-and-forget, 
            // the job succeeds but the broker might reject the message, leading to data loss.
            kafkaTemplate.send(record).get(properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
            
            int payloadSize = job.payload().getBytes(StandardCharsets.UTF_8).length;
            log.info("Successfully relayed message to Kafka: topic={}, key={}, size={} bytes", 
                job.topic(), job.key(), payloadSize);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaBrokerException("Interrupted while sending to Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            // Throwing this triggers the Platform's Retry Policy (Exponential Backoff)
            log.error("Kafka Broker unreachable or timed out. Will retry. Topic: {}, Key: {}", 
                job.topic(), job.key(), e);
            throw new KafkaBrokerException("Kafka Broker unreachable or timed out", e);
        }
    }

    /**
     * Validates that the payload size does not exceed the configured maximum.
     * 
     * @param job the job to validate
     * @throws PayloadTooLargeException if the payload exceeds the maximum size
     */
    private void validatePayloadSize(KafkaRelayJob job) {
        int payloadSize = job.payload().getBytes(StandardCharsets.UTF_8).length;
        if (payloadSize > properties.getMaxMessageSize()) {
            // Fail permanently - retrying won't fix this
            String errorMsg = String.format(
                "Payload size (%d bytes) exceeds Kafka max message size (%d bytes). " +
                "This message cannot be delivered and must be moved to dead letter queue. " +
                "Topic: %s, Key: %s",
                payloadSize, properties.getMaxMessageSize(), job.topic(), job.key()
            );
            log.error(errorMsg);
            throw new PayloadTooLargeException(errorMsg);
        }
    }

    /**
     * Creates a Kafka ProducerRecord from the job.
     * 
     * @param job the job containing the message data
     * @return a configured ProducerRecord
     */
    private ProducerRecord<String, String> createProducerRecord(KafkaRelayJob job) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
            job.topic(), 
            job.key(), 
            job.payload()
        );
        
        // Add headers if provided
        if (job.headers() != null) {
            job.headers().forEach((k, v) -> 
                record.headers().add(k, v.getBytes(StandardCharsets.UTF_8))
            );
        }
        
        return record;
    }
}
