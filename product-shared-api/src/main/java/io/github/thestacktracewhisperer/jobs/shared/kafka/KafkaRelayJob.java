package io.github.thestacktracewhisperer.jobs.shared.kafka;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

import java.util.Map;

/**
 * Generic job definition for relaying messages to Kafka.
 * This job holds a serialized payload to be published to a Kafka topic.
 * 
 * <p>Business logic should enqueue this job instead of publishing to Kafka directly.
 * A dedicated worker will pick this up and publish to Kafka, ensuring that events
 * are published if and only if the business transaction succeeds.</p>
 * 
 * <p>This eliminates "Dual Write" bugs - if the DB transaction rolls back, 
 * the Kafka message is never sent.</p>
 *
 * @param topic the Kafka topic to publish to
 * @param key the message key (used for partitioning)
 * @param payload the message payload as a JSON string
 * @param headers optional Kafka headers to include with the message
 */
public record KafkaRelayJob(
    String topic,
    String key,
    String payload,
    Map<String, String> headers
) implements Job {
    
    /**
     * Uses a dedicated queue to prevent batch jobs from blocking real-time event publishing.
     * This queue should be configured with appropriate concurrency settings based on
     * ordering requirements.
     */
    @Override
    public String queueName() {
        return "KAFKA_RELAY";
    }
}
