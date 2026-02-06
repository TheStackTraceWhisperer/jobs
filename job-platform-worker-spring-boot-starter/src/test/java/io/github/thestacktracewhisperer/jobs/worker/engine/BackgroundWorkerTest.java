package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.worker.dispatcher.JobRoutingEngine;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackgroundWorkerTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobRoutingEngine routingEngine;

    @Mock
    private JobWorkerProperties properties;

    @Mock
    private JobEnqueuer jobEnqueuer;

    @Mock
    private JobClaimService jobClaimService;

    @Mock
    private JobMetricsService metricsService;

    private BackgroundWorker backgroundWorker;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getConcurrency()).thenReturn(5);
        lenient().when(properties.getQueueName()).thenReturn("DEFAULT");
        lenient().when(properties.getMaxAttempts()).thenReturn(3);
        lenient().when(properties.getShutdownTimeoutSeconds()).thenReturn(1L);
        
        Set<String> supportedTypes = new HashSet<>(Arrays.asList("TestJob", "AnotherJob"));
        lenient().when(routingEngine.getRegisteredJobTypes()).thenReturn(supportedTypes);

        backgroundWorker = new BackgroundWorker(
            jobRepository, routingEngine, properties, jobEnqueuer, 
            jobClaimService, metricsService
        );
    }

    @AfterEach
    void cleanup() {
        JobContextHolder.clear();
        if (backgroundWorker != null) {
            backgroundWorker.shutdown();
        }
    }

    @Test
    void testInitialize() {
        backgroundWorker.initialize();

        verify(metricsService).registerWorkerActiveGauge(
            eq("DEFAULT"), any(), eq(5));
        verify(metricsService).registerWorkerPermitsAvailableGauge(
            eq("DEFAULT"), any());
    }

    @Test
    void testPollAndExecuteWithEmptyResults() {
        backgroundWorker.initialize();

        when(jobClaimService.fetchAndClaimJobs(any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());

        backgroundWorker.pollAndExecute();

        verify(jobClaimService).fetchAndClaimJobs(any(), eq("DEFAULT"), anyInt());
        verify(metricsService).recordPollerLoop("empty");
        verify(metricsService).recordDbPollTime(eq("DEFAULT"), any(Duration.class));
    }

    @Test
    void testPollAndExecuteRecordsDbPollTime() {
        backgroundWorker.initialize();

        when(jobClaimService.fetchAndClaimJobs(any(), any(), anyInt()))
            .thenReturn(Collections.emptyList());

        backgroundWorker.pollAndExecute();

        verify(metricsService).recordDbPollTime(eq("DEFAULT"), any(Duration.class));
    }

    @Test
    void testPollAndExecuteHandlesException() {
        backgroundWorker.initialize();

        when(jobClaimService.fetchAndClaimJobs(any(), any(), anyInt()))
            .thenThrow(new RuntimeException("Test exception"));

        assertDoesNotThrow(() -> backgroundWorker.pollAndExecute());
    }

    @Test
    void testShutdownGracefully() {
        backgroundWorker.initialize();

        assertDoesNotThrow(() -> backgroundWorker.shutdown());
    }

    @Test
    void testShutdownWhenNotInitialized() {
        assertDoesNotThrow(() -> backgroundWorker.shutdown());
    }

    @Test
    void testPropertiesUsed() {
        backgroundWorker.initialize();
        
        verify(properties, atLeast(1)).getConcurrency();
        verify(properties, atLeast(1)).getQueueName();
    }

    private JobEntity createJobEntity(String jobType) {
        JobEntity entity = new JobEntity("DEFAULT", jobType, "{}");
        entity.setId(UUID.randomUUID());
        entity.setStatus(JobStatus.PROCESSING);
        entity.setAttempts(1);
        entity.setRunAt(java.time.Instant.now().minusSeconds(10));
        return entity;
    }
}
