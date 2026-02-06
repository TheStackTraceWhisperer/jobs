package io.github.thestacktracewhisperer.jobs.producer.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobContextHolderTest {

    @AfterEach
    void cleanup() {
        JobContextHolder.clear();
    }

    @Test
    void testSetAndGetCurrentJobId() {
        UUID jobId = UUID.randomUUID();
        
        JobContextHolder.setCurrentJobId(jobId);
        
        assertEquals(jobId, JobContextHolder.getCurrentJobId());
    }

    @Test
    void testGetCurrentJobIdReturnsNullByDefault() {
        assertNull(JobContextHolder.getCurrentJobId());
    }

    @Test
    void testClear() {
        UUID jobId = UUID.randomUUID();
        JobContextHolder.setCurrentJobId(jobId);
        
        JobContextHolder.clear();
        
        assertNull(JobContextHolder.getCurrentJobId());
    }

    @Test
    void testHasCurrentJobId() {
        assertFalse(JobContextHolder.hasCurrentJobId());
        
        JobContextHolder.setCurrentJobId(UUID.randomUUID());
        
        assertTrue(JobContextHolder.hasCurrentJobId());
    }

    @Test
    void testHasCurrentJobIdAfterClear() {
        JobContextHolder.setCurrentJobId(UUID.randomUUID());
        JobContextHolder.clear();
        
        assertFalse(JobContextHolder.hasCurrentJobId());
    }

    @Test
    void testThreadLocal() throws InterruptedException {
        UUID mainThreadJobId = UUID.randomUUID();
        JobContextHolder.setCurrentJobId(mainThreadJobId);
        
        UUID[] otherThreadJobId = new UUID[1];
        Thread thread = new Thread(() -> {
            // Should not see the main thread's job ID
            assertNull(JobContextHolder.getCurrentJobId());
            assertFalse(JobContextHolder.hasCurrentJobId());
            
            // Set a different job ID
            UUID threadJobId = UUID.randomUUID();
            JobContextHolder.setCurrentJobId(threadJobId);
            otherThreadJobId[0] = threadJobId;
            
            assertEquals(threadJobId, JobContextHolder.getCurrentJobId());
        });
        
        thread.start();
        thread.join();
        
        // Main thread should still have its original job ID
        assertEquals(mainThreadJobId, JobContextHolder.getCurrentJobId());
        assertNotEquals(otherThreadJobId[0], JobContextHolder.getCurrentJobId());
    }

    @Test
    void testSetCurrentJobIdWithNull() {
        JobContextHolder.setCurrentJobId(UUID.randomUUID());
        
        JobContextHolder.setCurrentJobId(null);
        
        assertNull(JobContextHolder.getCurrentJobId());
        assertFalse(JobContextHolder.hasCurrentJobId());
    }

    @Test
    void testMultipleSetCalls() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        
        JobContextHolder.setCurrentJobId(jobId1);
        assertEquals(jobId1, JobContextHolder.getCurrentJobId());
        
        JobContextHolder.setCurrentJobId(jobId2);
        assertEquals(jobId2, JobContextHolder.getCurrentJobId());
    }
}
