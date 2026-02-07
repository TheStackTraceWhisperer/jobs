package io.github.thestacktracewhisperer.jobs.kafka.handler;

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
 * <p>If sending fails due to broker unavailability or timeout, the handler throws an exception
 * which triggers the Platform's Retry Policy with exponential backoff.</p>
 * 
 * <p><b>Risk Mitigation:</b> To prevent the "Retry Storm" amplification issue, the handler
 * checks payload size before sending. If the payload exceeds Kafka's message size limit,
 * the handler fails permanently rather than retrying indefinitely.</p>
 */
@Component
@Slf4j
public class KafkaRelayHandler implements JobHandler<KafkaRelayJob> {

    private final KafkaTemplate<String, String> kafkaTemplate;
    
    /**
     * Default Kafka max message size (1MB). This should match your broker's message.max.bytes setting.
     * If your broker allows larger messages, configure this value accordingly.
     */
    private static final int MAX_MESSAGE_SIZE = 1024 * 1024; // 1MB
    
    public KafkaRelayHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void handle(KafkaRelayJob job) throws Exception {
        // Risk Mitigation: Check payload size before attempting to send
        int payloadSize = job.payload().getBytes(StandardCharsets.UTF_8).length;
        if (payloadSize > MAX_MESSAGE_SIZE) {
            // Fail permanently - retrying won't fix this
            String errorMsg = String.format(
                "Payload size (%d bytes) exceeds Kafka max message size (%d bytes). " +
                "This message cannot be delivered and must be moved to dead letter queue. " +
                "Topic: %s, Key: %s",
                payloadSize, MAX_MESSAGE_SIZE, job.topic(), job.key()
            );
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        
        try {
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

            // CRITICAL: Synchronous Send
            // We must wait for the broker ACK. If we fire-and-forget, 
            // the job succeeds but the broker might reject the message, leading to data loss.
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            
            log.info("Successfully relayed message to Kafka: topic={}, key={}, size={} bytes", 
                job.topic(), job.key(), payloadSize);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while sending to Kafka", e);
        } catch (ExecutionException | TimeoutException e) {
            // Throwing this triggers the Platform's Retry Policy (Exponential Backoff)
            log.error("Kafka Broker unreachable or timed out. Will retry. Topic: {}, Key: {}", 
                job.topic(), job.key(), e);
            throw new RuntimeException("Kafka Broker unreachable or timed out", e);
        }
    }
}
