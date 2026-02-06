package io.github.thestacktracewhisperer.jobs.worker.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobWorkerPropertiesTest {

    @Test
    void testDefaultValues() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        assertFalse(properties.isEnabled());
        assertEquals(10, properties.getConcurrency());
        assertEquals("DEFAULT", properties.getQueueName());
        assertEquals(3, properties.getMaxAttempts());
        assertEquals(5, properties.getZombieThresholdMinutes());
        assertEquals(1000, properties.getPollingIntervalMs());
        assertEquals(60000, properties.getReaperIntervalMs());
        assertEquals(30, properties.getShutdownTimeoutSeconds());
    }

    @Test
    void testSetEnabled() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
        
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }

    @Test
    void testSetConcurrency() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setConcurrency(20);
        assertEquals(20, properties.getConcurrency());
    }

    @Test
    void testSetQueueName() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setQueueName("CUSTOM_QUEUE");
        assertEquals("CUSTOM_QUEUE", properties.getQueueName());
    }

    @Test
    void testSetMaxAttempts() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setMaxAttempts(5);
        assertEquals(5, properties.getMaxAttempts());
    }

    @Test
    void testSetZombieThresholdMinutes() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setZombieThresholdMinutes(10);
        assertEquals(10, properties.getZombieThresholdMinutes());
    }

    @Test
    void testSetPollingIntervalMs() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setPollingIntervalMs(2000);
        assertEquals(2000, properties.getPollingIntervalMs());
    }

    @Test
    void testSetReaperIntervalMs() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setReaperIntervalMs(120000);
        assertEquals(120000, properties.getReaperIntervalMs());
    }

    @Test
    void testSetShutdownTimeoutSeconds() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setShutdownTimeoutSeconds(60);
        assertEquals(60, properties.getShutdownTimeoutSeconds());
    }

    @Test
    void testAllSettersAndGetters() {
        JobWorkerProperties properties = new JobWorkerProperties();
        
        properties.setEnabled(true);
        properties.setConcurrency(15);
        properties.setQueueName("TEST");
        properties.setMaxAttempts(7);
        properties.setZombieThresholdMinutes(15);
        properties.setPollingIntervalMs(500);
        properties.setReaperIntervalMs(30000);
        properties.setShutdownTimeoutSeconds(45);
        
        assertTrue(properties.isEnabled());
        assertEquals(15, properties.getConcurrency());
        assertEquals("TEST", properties.getQueueName());
        assertEquals(7, properties.getMaxAttempts());
        assertEquals(15, properties.getZombieThresholdMinutes());
        assertEquals(500, properties.getPollingIntervalMs());
        assertEquals(30000, properties.getReaperIntervalMs());
        assertEquals(45, properties.getShutdownTimeoutSeconds());
    }
}
