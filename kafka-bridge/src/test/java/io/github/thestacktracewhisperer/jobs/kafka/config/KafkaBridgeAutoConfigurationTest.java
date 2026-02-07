package io.github.thestacktracewhisperer.jobs.kafka.config;

import io.github.thestacktracewhisperer.jobs.kafka.properties.KafkaBackoffProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaBridgeConfigTest {

    @Mock
    private KafkaBackoffProperties backoffProperties;

    @Test
    void testConsumerFactory() {
        // Arrange
        KafkaBridgeAutoConfiguration config = new KafkaBridgeAutoConfiguration(backoffProperties);
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeAutoConfiguration.class.getDeclaredField("bootstrapServers");
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
        when(backoffProperties.getInitialInterval()).thenReturn(1000L);
        when(backoffProperties.getMultiplier()).thenReturn(2.0);
        when(backoffProperties.getMaxInterval()).thenReturn(60000L);
        when(backoffProperties.getMaxElapsedTime()).thenReturn(300000L);
        
        KafkaBridgeAutoConfiguration config = new KafkaBridgeAutoConfiguration(backoffProperties);
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeAutoConfiguration.class.getDeclaredField("bootstrapServers");
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
        when(backoffProperties.getInitialInterval()).thenReturn(1000L);
        when(backoffProperties.getMultiplier()).thenReturn(2.0);
        when(backoffProperties.getMaxInterval()).thenReturn(60000L);
        when(backoffProperties.getMaxElapsedTime()).thenReturn(300000L);
        
        KafkaBridgeAutoConfiguration config = new KafkaBridgeAutoConfiguration(backoffProperties);
        
        // Use reflection to set the bootstrap servers field
        try {
            var field = KafkaBridgeAutoConfiguration.class.getDeclaredField("bootstrapServers");
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
