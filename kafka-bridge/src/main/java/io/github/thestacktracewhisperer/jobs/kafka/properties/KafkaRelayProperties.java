package io.github.thestacktracewhisperer.jobs.kafka.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kafka relay handler.
 */
@Data
@ConfigurationProperties(prefix = "kafka.relay")
public class KafkaRelayProperties {
    
    /**
     * Maximum message size in bytes. Defaults to 1MB.
     * Should match your broker's message.max.bytes setting.
     */
    private int maxMessageSize = 1024 * 1024; // 1MB default
    
    /**
     * Timeout for synchronous send operations in seconds. Defaults to 10 seconds.
     */
    private int sendTimeoutSeconds = 10;
}
