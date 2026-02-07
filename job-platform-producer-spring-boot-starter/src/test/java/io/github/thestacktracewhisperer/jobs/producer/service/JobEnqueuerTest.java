package io.github.thestacktracewhisperer.jobs.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSerializationException;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobEnqueuerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JobMetricsService metricsService;

    private JobEnqueuer jobEnqueuer;

    private static class TestJob implements Job {
        public String data;

        public TestJob() {}

        public TestJob(String data) {
            this.data = data;
        }
    }

    private static class CustomQueueJob implements Job {
        @Override
        public String queueName() {
            return "CUSTOM";
        }
    }

    @BeforeEach
    void setUp() {
        jobEnqueuer = new JobEnqueuer(jobRepository, objectMapper, metricsService);
    }

    @AfterEach
    void cleanup() {
        JobContextHolder.clear();
        MDC.clear();
    }

    @Test
    void testEnqueueWithDefaultQueue() throws Exception {
        TestJob job = new TestJob("test data");
        String payload = "{\"data\":\"test data\"}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);
        savedEntity.setId(UUID.randomUUID());

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        assertNotNull(result);
        assertEquals(savedEntity.getId(), result.getId());

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals("DEFAULT", capturedEntity.getQueueName());
        assertEquals(TestJob.class.getName(), capturedEntity.getJobType());
        assertEquals(payload, capturedEntity.getPayload());

        verify(metricsService).recordEnqueueTime(eq(TestJob.class.getName()), any());
        verify(metricsService).recordJobEnqueued(TestJob.class.getName(), "DEFAULT");
    }

    @Test
    void testEnqueueWithCustomQueue() throws Exception {
        CustomQueueJob job = new CustomQueueJob();
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("CUSTOM", CustomQueueJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals("CUSTOM", capturedEntity.getQueueName());
    }

    @Test
    void testEnqueueWithRunAt() throws Exception {
        TestJob job = new TestJob("test");
        Instant runAt = Instant.now().plusSeconds(3600);
        String payload = "{\"data\":\"test\"}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job, runAt);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals(runAt, capturedEntity.getRunAt());
    }

    @Test
    void testEnqueueWithNullRunAt() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{\"data\":\"test\"}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job, null);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertNotNull(capturedEntity.getRunAt());
    }

    @Test
    void testEnqueueWithMDCTraceId() throws Exception {
        UUID traceId = UUID.randomUUID();
        MDC.put("traceId", traceId.toString());

        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals(traceId, capturedEntity.getTraceId());
    }

    @Test
    void testEnqueueWithInvalidMDCTraceId() throws Exception {
        MDC.put("traceId", "not-a-valid-uuid");

        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertNull(capturedEntity.getTraceId());
    }

    @Test
    void testEnqueueWithParentJobId() throws Exception {
        UUID parentJobId = UUID.randomUUID();
        JobContextHolder.setCurrentJobId(parentJobId);

        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals(parentJobId, capturedEntity.getParentJobId());
    }

    @Test
    void testEnqueueWithoutParentJobId() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());

        JobEntity capturedEntity = entityCaptor.getValue();
        assertNull(capturedEntity.getParentJobId());
    }

    @Test
    void testEnqueueThrowsSerializationException() throws Exception {
        TestJob job = new TestJob("test");
        
        when(objectMapper.writeValueAsString(job))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});

        assertThrows(JobSerializationException.class, () -> {
            jobEnqueuer.enqueue(job);
        });

        verify(jobRepository, never()).save(any());
        verify(metricsService, never()).recordEnqueueTime(any(), any());
        verify(metricsService, never()).recordJobEnqueued(any(), any());
    }

    @Test
    void testEnqueueOneParameterCallsTwoParameterMethod() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        JobEntity result = jobEnqueuer.enqueue(job);

        assertNotNull(result);
        verify(jobRepository).save(any(JobEntity.class));
    }

    @Test
    void testEnqueueRecordsMetrics() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);

        jobEnqueuer.enqueue(job);

        verify(metricsService).recordEnqueueTime(eq(TestJob.class.getName()), any());
        verify(metricsService).recordJobEnqueued(TestJob.class.getName(), "DEFAULT");
    }

    @Test
    void testEnqueueSucceedsWhenMetricsServiceFails() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);
        savedEntity.setId(UUID.randomUUID());

        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);
        
        // Mock metricsService to throw a RuntimeException
        doThrow(new RuntimeException("Metrics service is down"))
            .when(metricsService).recordEnqueueTime(any(), any());

        // Assert that enqueue() still returns a saved JobEntity and does not throw
        JobEntity result = assertDoesNotThrow(() -> jobEnqueuer.enqueue(job));
        
        assertNotNull(result);
        assertEquals(savedEntity.getId(), result.getId());
        verify(jobRepository).save(any(JobEntity.class));
    }
    
    // ============================================================================
    // BULK ENQUEUE TESTS
    // ============================================================================
    
    @Test
    void testBulkEnqueue() throws Exception {
        List<Job> jobs = Arrays.asList(
            new TestJob("job1"),
            new TestJob("job2"),
            new TestJob("job3")
        );
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        List<JobEntity> savedEntities = Arrays.asList(
            createJobEntity("job1"),
            createJobEntity("job2"),
            createJobEntity("job3")
        );
        
        when(jobRepository.saveAll(anyList())).thenReturn(savedEntities);
        
        List<JobEntity> result = jobEnqueuer.enqueue(jobs);
        
        assertNotNull(result);
        assertEquals(3, result.size());
        
        verify(jobRepository).saveAll(anyList());
        verify(metricsService, times(3)).recordEnqueueTime(eq(TestJob.class.getName()), any());
        verify(metricsService, times(3)).recordJobEnqueued(eq(TestJob.class.getName()), eq("DEFAULT"));
        verify(metricsService).recordBatchSize(3);
    }
    
    @Test
    void testBulkEnqueueWithEmptyList() {
        List<Job> jobs = new ArrayList<>();
        
        List<JobEntity> result = jobEnqueuer.enqueue(jobs);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(jobRepository, never()).saveAll(any());
        verify(metricsService, never()).recordBatchSize(anyInt());
    }
    
    @Test
    void testBulkEnqueueWithNullList() {
        List<JobEntity> result = jobEnqueuer.enqueue((List<Job>) null);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(jobRepository, never()).saveAll(any());
    }
    
    @Test
    void testBulkEnqueueCapturesParentJobId() throws Exception {
        UUID parentJobId = UUID.randomUUID();
        JobContextHolder.setCurrentJobId(parentJobId);
        
        List<Job> jobs = Arrays.asList(
            new TestJob("job1"),
            new TestJob("job2")
        );
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        ArgumentCaptor<List<JobEntity>> captor = ArgumentCaptor.forClass(List.class);
        when(jobRepository.saveAll(captor.capture())).thenReturn(new ArrayList<>());
        
        jobEnqueuer.enqueue(jobs);
        
        List<JobEntity> capturedEntities = captor.getValue();
        assertEquals(2, capturedEntities.size());
        
        for (JobEntity entity : capturedEntities) {
            assertEquals(parentJobId, entity.getParentJobId());
        }
    }
    
    @Test
    void testBulkEnqueueCapturesMDCTraceId() throws Exception {
        UUID traceId = UUID.randomUUID();
        MDC.put("traceId", traceId.toString());
        
        List<Job> jobs = Arrays.asList(
            new TestJob("job1"),
            new TestJob("job2")
        );
        
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        ArgumentCaptor<List<JobEntity>> captor = ArgumentCaptor.forClass(List.class);
        when(jobRepository.saveAll(captor.capture())).thenReturn(new ArrayList<>());
        
        jobEnqueuer.enqueue(jobs);
        
        List<JobEntity> capturedEntities = captor.getValue();
        assertEquals(2, capturedEntities.size());
        
        for (JobEntity entity : capturedEntities) {
            assertEquals(traceId, entity.getTraceId());
        }
    }
    
    @Test
    void testBulkEnqueueThrowsSerializationException() throws Exception {
        List<Job> jobs = Arrays.asList(
            new TestJob("job1"),
            new TestJob("job2")
        );
        
        when(objectMapper.writeValueAsString(any()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});
        
        assertThrows(JobSerializationException.class, () -> {
            jobEnqueuer.enqueue(jobs);
        });
        
        verify(jobRepository, never()).saveAll(any());
    }
    
    // ============================================================================
    // PRIORITY TESTS
    // ============================================================================
    
    private static class HighPriorityJob implements Job {
        @Override
        public int priority() {
            return 10;
        }
    }
    
    @Test
    void testEnqueueWithPriority() throws Exception {
        HighPriorityJob job = new HighPriorityJob();
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", HighPriorityJob.class.getName(), payload);
        
        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);
        
        jobEnqueuer.enqueue(job);
        
        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());
        
        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals(10, capturedEntity.getPriority());
    }
    
    @Test
    void testEnqueueWithDefaultPriority() throws Exception {
        TestJob job = new TestJob("test");
        String payload = "{}";
        JobEntity savedEntity = new JobEntity("DEFAULT", TestJob.class.getName(), payload);
        
        when(objectMapper.writeValueAsString(job)).thenReturn(payload);
        when(jobRepository.save(any(JobEntity.class))).thenReturn(savedEntity);
        
        jobEnqueuer.enqueue(job);
        
        ArgumentCaptor<JobEntity> entityCaptor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(entityCaptor.capture());
        
        JobEntity capturedEntity = entityCaptor.getValue();
        assertEquals(0, capturedEntity.getPriority());
    }
    
    private JobEntity createJobEntity(String data) {
        JobEntity entity = new JobEntity("DEFAULT", TestJob.class.getName(), "{}");
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
