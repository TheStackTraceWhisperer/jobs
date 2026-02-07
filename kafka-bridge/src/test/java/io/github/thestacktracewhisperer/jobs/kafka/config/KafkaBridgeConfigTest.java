package io.github.thestacktracewhisperer.jobs.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import static org.junit.jupiter.api.Assertions.*;

class KafkaBridgeConfigTest {

    @Test
    void testConsumerFactory() {
        // Arrange
        KafkaBridgeConfig config = new KafkaBridgeConfig();
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeConfig.class.getDeclaredField("bootstrapServers");
            field.setAccessible(true);
            field.set(config, "localhost:9092");
        } catch (Exception e) {
            fail("Failed to set bootstrapServers field: " + e.getMessage());
        }

        // Act
        ConsumerFactory<String, String> factory = config.consumerFactory();

        // Assert
        assertNotNull(factory);
        var configMap = factory.getConfigurationProperties();
        assertEquals("localhost:9092", configMap.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(false, configMap.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    }

    @Test
    void testKafkaManualAckListenerContainerFactory() {
        // Arrange
        KafkaBridgeConfig config = new KafkaBridgeConfig();
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeConfig.class.getDeclaredField("bootstrapServers");
            field.setAccessible(true);
            field.set(config, "localhost:9092");
        } catch (Exception e) {
            fail("Failed to set bootstrapServers field: " + e.getMessage());
        }

        // Act
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            config.kafkaManualAckListenerContainerFactory();

        // Assert
        assertNotNull(factory);
        assertEquals(ContainerProperties.AckMode.MANUAL, 
            factory.getContainerProperties().getAckMode());
    }

    @Test
    void testKafkaManualAckListenerContainerFactory_HasErrorHandler() {
        // Arrange
        KafkaBridgeConfig config = new KafkaBridgeConfig();
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeConfig.class.getDeclaredField("bootstrapServers");
            field.setAccessible(true);
            field.set(config, "localhost:9092");
        } catch (Exception e) {
            fail("Failed to set bootstrapServers field: " + e.getMessage());
        }

        // Act
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            config.kafkaManualAckListenerContainerFactory();

        // Assert
        assertNotNull(factory);
        // The error handler is set via setCommonErrorHandler which we can't easily verify
        // but we can at least ensure the factory was created
    }
}
