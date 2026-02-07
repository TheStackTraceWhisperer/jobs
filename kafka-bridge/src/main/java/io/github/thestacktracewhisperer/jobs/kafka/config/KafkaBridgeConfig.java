package io.github.thestacktracewhisperer.jobs.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the bridge components.
 * 
 * <p>This configuration provides a manual acknowledgment listener container factory
 * for Kafka consumers that need transactional safety when processing messages.</p>
 * 
 * <p><b>Risk Mitigation:</b> Configures exponential backoff error handling to avoid
 * the "Retry Storm" amplification issue. This prevents consumers from hammering
 * external systems (e.g., databases) during an outage.</p>
 */
@Configuration
@EnableKafka
public class KafkaBridgeConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates a consumer factory for Kafka listeners.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual ack
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a listener container factory with manual acknowledgment mode.
     * 
     * <p>Manual acknowledgment is required to ensure we don't commit offset
     * until the job is successfully enqueued in the database.</p>
     * 
     * <p>Also configures exponential backoff for error handling to prevent
     * retry storms during database outages.</p>
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaManualAckListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Enable manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Configure exponential backoff error handler
        // This prevents retry storms during DB outages
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);  // Start with 1 second
        backOff.setMultiplier(2.0);          // Double each time
        backOff.setMaxInterval(60000L);      // Cap at 60 seconds
        backOff.setMaxElapsedTime(300000L);  // Give up after 5 minutes
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}
