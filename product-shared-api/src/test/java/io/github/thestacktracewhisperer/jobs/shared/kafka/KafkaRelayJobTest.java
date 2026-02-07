package io.github.thestacktracewhisperer.jobs.shared.kafka;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaRelayJobTest {

    @Test
    void testQueueName() {
        // Arrange
        KafkaRelayJob job = new KafkaRelayJob("topic", "key", "payload", null);
        
        // Act
        String queueName = job.queueName();
        
        // Assert
        assertEquals("KAFKA_RELAY", queueName);
    }

    @Test
    void testRecordCreation() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"test\":\"data\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("correlation-id", "123");
        
        // Act
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, headers);
        
        // Assert
        assertEquals(topic, job.topic());
        assertEquals(key, job.key());
        assertEquals(payload, job.payload());
        assertEquals(headers, job.headers());
    }

    @Test
    void testRecordCreationWithoutHeaders() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"test\":\"data\"}";
        
        // Act
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, null);
        
        // Assert
        assertEquals(topic, job.topic());
        assertEquals(key, job.key());
        assertEquals(payload, job.payload());
        assertNull(job.headers());
    }
}
