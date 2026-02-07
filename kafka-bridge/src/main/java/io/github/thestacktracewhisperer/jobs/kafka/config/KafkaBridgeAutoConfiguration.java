package io.github.thestacktracewhisperer.jobs.kafka.config;

import io.github.thestacktracewhisperer.jobs.kafka.properties.KafkaBackoffProperties;
import io.github.thestacktracewhisperer.jobs.kafka.properties.KafkaRelayProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Kafka bridge components.
 * 
 * <p>This auto-configuration provides:
 * <ul>
 *   <li>Manual acknowledgment listener container factory for transactional Kafka consumers</li>
 *   <li>Exponential backoff error handling to prevent retry storms</li>
 *   <li>Automatic registration of KafkaRelayHandler when KafkaTemplate is available</li>
 * </ul>
 * 
 * <p>To enable this auto-configuration, simply add the kafka-bridge dependency to your project.
 * No explicit @Import or component scanning is required.</p>
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableKafka
@EnableConfigurationProperties({KafkaBackoffProperties.class, KafkaRelayProperties.class})
@ComponentScan(basePackages = "io.github.thestacktracewhisperer.jobs.kafka")
@RequiredArgsConstructor
public class KafkaBridgeAutoConfiguration {

    private final KafkaBackoffProperties backoffProperties;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates a consumer factory for Kafka listeners.
     */
    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaManualAckListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Enable manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Configure exponential backoff error handler
        // This prevents retry storms during DB outages
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(backoffProperties.getInitialInterval());
        backOff.setMultiplier(backoffProperties.getMultiplier());
        backOff.setMaxInterval(backoffProperties.getMaxInterval());
        backOff.setMaxElapsedTime(backoffProperties.getMaxElapsedTime());
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}
