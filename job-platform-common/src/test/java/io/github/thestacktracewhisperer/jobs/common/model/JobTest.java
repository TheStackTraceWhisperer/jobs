package io.github.thestacktracewhisperer.jobs.common.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    private static class TestJob implements Job {
        // Uses default queueName
    }

    private static class CustomQueueJob implements Job {
        @Override
        public String queueName() {
            return "CUSTOM_QUEUE";
        }
    }

    @Test
    void testDefaultQueueName() {
        Job job = new TestJob();
        
        assertEquals("DEFAULT", job.queueName());
    }

    @Test
    void testCustomQueueName() {
        Job job = new CustomQueueJob();
        
        assertEquals("CUSTOM_QUEUE", job.queueName());
    }

    @Test
    void testJobIsInterface() {
        assertTrue(Job.class.isInterface());
    }

    @Test
    void testImplementation() {
        Job job = new TestJob();
        
        assertNotNull(job);
        assertTrue(job instanceof Job);
    }
}
