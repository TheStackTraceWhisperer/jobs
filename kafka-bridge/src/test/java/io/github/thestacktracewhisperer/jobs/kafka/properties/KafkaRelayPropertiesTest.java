package io.github.thestacktracewhisperer.jobs.kafka.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaRelayPropertiesTest {

    @Test
    void testDefaultValues() {
        // Arrange & Act
        KafkaRelayProperties properties = new KafkaRelayProperties();
        
        // Assert
        assertEquals(1024 * 1024, properties.getMaxMessageSize()); // 1MB
        assertEquals(10, properties.getSendTimeoutSeconds());
    }

    @Test
    void testSetters() {
        // Arrange
        KafkaRelayProperties properties = new KafkaRelayProperties();
        
        // Act
        properties.setMaxMessageSize(2048 * 1024); // 2MB
        properties.setSendTimeoutSeconds(30);
        
        // Assert
        assertEquals(2048 * 1024, properties.getMaxMessageSize());
        assertEquals(30, properties.getSendTimeoutSeconds());
    }
}
