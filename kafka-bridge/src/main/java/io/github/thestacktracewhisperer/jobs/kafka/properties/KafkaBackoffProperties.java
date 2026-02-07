package io.github.thestacktracewhisperer.jobs.kafka.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka consumer backoff behavior.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka.consumer.backoff")
public class KafkaBackoffProperties {
    
    /**
     * Initial backoff interval in milliseconds. Defaults to 1 second.
     */
    private long initialInterval = 1000L;
    
    /**
     * Multiplier for exponential backoff. Defaults to 2.0 (doubles each time).
     */
    private double multiplier = 2.0;
    
    /**
     * Maximum backoff interval in milliseconds. Defaults to 60 seconds.
     */
    private long maxInterval = 60000L;
    
    /**
     * Maximum elapsed time before giving up in milliseconds. Defaults to 5 minutes.
     */
    private long maxElapsedTime = 300000L;
}
