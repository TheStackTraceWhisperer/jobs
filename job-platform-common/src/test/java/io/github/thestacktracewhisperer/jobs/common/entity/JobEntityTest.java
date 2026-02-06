package io.github.thestacktracewhisperer.jobs.common.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobEntityTest {

    @Test
    void testDefaultConstructor() {
        JobEntity entity = new JobEntity();
        
        assertNull(entity.getId());
        assertEquals("DEFAULT", entity.getQueueName());
        assertNull(entity.getJobType());
        assertNull(entity.getPayload());
        assertEquals(JobStatus.QUEUED, entity.getStatus());
        assertEquals(0, entity.getAttempts());
        assertNotNull(entity.getRunAt());
        assertNull(entity.getLastHeartbeat());
        assertEquals(0, entity.getVersion());
        assertNull(entity.getTraceId());
        assertNull(entity.getParentJobId());
        assertNotNull(entity.getCreatedAt());
        assertNull(entity.getLastError());
    }

    @Test
    void testParameterizedConstructor() {
        String queueName = "test-queue";
        String jobType = "com.example.TestJob";
        String payload = "{\"data\":\"test\"}";
        
        JobEntity entity = new JobEntity(queueName, jobType, payload);
        
        assertEquals(queueName, entity.getQueueName());
        assertEquals(jobType, entity.getJobType());
        assertEquals(payload, entity.getPayload());
        assertEquals(JobStatus.QUEUED, entity.getStatus());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getRunAt());
    }

    @Test
    void testIncrementAttempts() {
        JobEntity entity = new JobEntity();
        assertEquals(0, entity.getAttempts());
        
        entity.incrementAttempts();
        assertEquals(1, entity.getAttempts());
        
        entity.incrementAttempts();
        assertEquals(2, entity.getAttempts());
    }

    @Test
    void testMarkAsProcessing() {
        JobEntity entity = new JobEntity();
        Instant beforeMark = Instant.now();
        
        entity.markAsProcessing();
        
        assertEquals(JobStatus.PROCESSING, entity.getStatus());
        assertNotNull(entity.getLastHeartbeat());
        assertTrue(entity.getLastHeartbeat().isAfter(beforeMark) || 
                   entity.getLastHeartbeat().equals(beforeMark));
    }

    @Test
    void testMarkAsSuccess() {
        JobEntity entity = new JobEntity();
        
        entity.markAsSuccess();
        
        assertEquals(JobStatus.SUCCESS, entity.getStatus());
    }

    @Test
    void testMarkAsFailed() {
        JobEntity entity = new JobEntity();
        String errorMessage = "Test error";
        
        entity.markAsFailed(errorMessage);
        
        assertEquals(JobStatus.FAILED, entity.getStatus());
        assertEquals(errorMessage, entity.getLastError());
    }

    @Test
    void testMarkAsPermanentlyFailed() {
        JobEntity entity = new JobEntity();
        String errorMessage = "Permanent error";
        
        entity.markAsPermanentlyFailed(errorMessage);
        
        assertEquals(JobStatus.PERMANENTLY_FAILED, entity.getStatus());
        assertEquals(errorMessage, entity.getLastError());
    }

    @Test
    void testOnCreate() {
        JobEntity entity = new JobEntity();
        entity.setCreatedAt(null);
        entity.setRunAt(null);
        
        entity.onCreate();
        
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getRunAt());
    }

    @Test
    void testOnCreateDoesNotOverrideExistingValues() {
        JobEntity entity = new JobEntity();
        Instant existingCreatedAt = Instant.now().minusSeconds(100);
        Instant existingRunAt = Instant.now().minusSeconds(50);
        
        entity.setCreatedAt(existingCreatedAt);
        entity.setRunAt(existingRunAt);
        
        entity.onCreate();
        
        assertEquals(existingCreatedAt, entity.getCreatedAt());
        assertEquals(existingRunAt, entity.getRunAt());
    }

    @Test
    void testSettersAndGetters() {
        JobEntity entity = new JobEntity();
        
        UUID id = UUID.randomUUID();
        entity.setId(id);
        assertEquals(id, entity.getId());
        
        String queueName = "custom-queue";
        entity.setQueueName(queueName);
        assertEquals(queueName, entity.getQueueName());
        
        String jobType = "com.example.CustomJob";
        entity.setJobType(jobType);
        assertEquals(jobType, entity.getJobType());
        
        String payload = "{\"custom\":\"data\"}";
        entity.setPayload(payload);
        assertEquals(payload, entity.getPayload());
        
        JobStatus status = JobStatus.PROCESSING;
        entity.setStatus(status);
        assertEquals(status, entity.getStatus());
        
        int attempts = 5;
        entity.setAttempts(attempts);
        assertEquals(attempts, entity.getAttempts());
        
        Instant runAt = Instant.now();
        entity.setRunAt(runAt);
        assertEquals(runAt, entity.getRunAt());
        
        Instant lastHeartbeat = Instant.now();
        entity.setLastHeartbeat(lastHeartbeat);
        assertEquals(lastHeartbeat, entity.getLastHeartbeat());
        
        int version = 3;
        entity.setVersion(version);
        assertEquals(version, entity.getVersion());
        
        UUID traceId = UUID.randomUUID();
        entity.setTraceId(traceId);
        assertEquals(traceId, entity.getTraceId());
        
        UUID parentJobId = UUID.randomUUID();
        entity.setParentJobId(parentJobId);
        assertEquals(parentJobId, entity.getParentJobId());
        
        Instant createdAt = Instant.now();
        entity.setCreatedAt(createdAt);
        assertEquals(createdAt, entity.getCreatedAt());
        
        String lastError = "Error message";
        entity.setLastError(lastError);
        assertEquals(lastError, entity.getLastError());
    }
}
