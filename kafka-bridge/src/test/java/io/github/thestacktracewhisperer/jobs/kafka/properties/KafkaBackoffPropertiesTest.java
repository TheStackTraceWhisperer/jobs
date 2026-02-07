package io.github.thestacktracewhisperer.jobs.kafka.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaBackoffPropertiesTest {

    @Test
    void testDefaultValues() {
        // Arrange & Act
        KafkaBackoffProperties properties = new KafkaBackoffProperties();
        
        // Assert
        assertEquals(1000L, properties.getInitialInterval());
        assertEquals(2.0, properties.getMultiplier());
        assertEquals(60000L, properties.getMaxInterval());
        assertEquals(300000L, properties.getMaxElapsedTime());
    }

    @Test
    void testSetters() {
        // Arrange
        KafkaBackoffProperties properties = new KafkaBackoffProperties();
        
        // Act
        properties.setInitialInterval(2000L);
        properties.setMultiplier(3.0);
        properties.setMaxInterval(120000L);
        properties.setMaxElapsedTime(600000L);
        
        // Assert
        assertEquals(2000L, properties.getInitialInterval());
        assertEquals(3.0, properties.getMultiplier());
        assertEquals(120000L, properties.getMaxInterval());
        assertEquals(600000L, properties.getMaxElapsedTime());
    }
}
