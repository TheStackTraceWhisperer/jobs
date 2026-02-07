package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobWorkerProperties properties;

    @Mock
    private JobMetricsService metricsService;

    private MeterRegistry meterRegistry;
    private MaintenanceService maintenanceService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        maintenanceService = new MaintenanceService(
            jobRepository, properties, metricsService, meterRegistry);
        
        lenient().when(properties.getZombieThresholdMinutes()).thenReturn(5);
    }

    @Test
    void testReapZombieJobsFindsAndResetsJobs() {
        JobEntity zombie1 = createZombieJob("Job1");
        JobEntity zombie2 = createZombieJob("Job2");
        List<JobEntity> zombies = Arrays.asList(zombie1, zombie2);

        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(zombies);
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        maintenanceService.reapZombieJobs();

        assertEquals(JobStatus.QUEUED, zombie1.getStatus());
        assertEquals(JobStatus.QUEUED, zombie2.getStatus());
        assertTrue(zombie1.getLastError().contains("timed out"));
        assertTrue(zombie2.getLastError().contains("timed out"));

        verify(jobRepository, times(2)).save(any(JobEntity.class));
        verify(metricsService, times(2)).recordZombieJobReaped(anyString());
        verify(metricsService).recordReaperExecutionTime(any(Duration.class));
    }

    @Test
    void testReapZombieJobsWithNoZombies() {
        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        maintenanceService.reapZombieJobs();

        verify(jobRepository, never()).save(any(JobEntity.class));
        verify(metricsService, never()).recordZombieJobReaped(anyString());
        verify(metricsService).recordReaperExecutionTime(any(Duration.class));
    }

    @Test
    void testReapZombieJobsHandlesException() {
        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> maintenanceService.reapZombieJobs());

        verify(metricsService).recordReaperExecutionTime(any(Duration.class));
    }

    @Test
    void testReapZombieJobsUsesCorrectThreshold() {
        when(properties.getZombieThresholdMinutes()).thenReturn(10);
        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(Collections.emptyList());

        maintenanceService.reapZombieJobs();

        verify(jobRepository).findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class));
    }

    @Test
    void testUpdateQueueMetrics() {
        Object[] queue1Data = new Object[]{"DEFAULT", 10L, Instant.now().minusSeconds(300)};
        Object[] queue2Data = new Object[]{"CUSTOM", 5L, Instant.now().minusSeconds(120)};
        List<Object[]> stats = Arrays.asList(queue1Data, queue2Data);

        when(jobRepository.getQueueStatisticsNative()).thenReturn(stats);

        maintenanceService.updateQueueMetrics();

        verify(jobRepository).getQueueStatisticsNative();

        var depthGauge1 = meterRegistry.find("jobs.queue.depth")
            .tag("queue", "DEFAULT")
            .gauge();
        assertNotNull(depthGauge1);
        assertEquals(10.0, depthGauge1.value());

        var depthGauge2 = meterRegistry.find("jobs.queue.depth")
            .tag("queue", "CUSTOM")
            .gauge();
        assertNotNull(depthGauge2);
        assertEquals(5.0, depthGauge2.value());
    }

    @Test
    void testUpdateQueueMetricsWithEmptyResults() {
        when(jobRepository.getQueueStatisticsNative()).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> maintenanceService.updateQueueMetrics());

        verify(jobRepository).getQueueStatisticsNative();
    }

    @Test
    void testUpdateQueueMetricsHandlesException() {
        when(jobRepository.getQueueStatisticsNative())
            .thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> maintenanceService.updateQueueMetrics());
    }

    @Test
    void testUpdateQueueMetricsCreatesAgeGauge() {
        Instant oldJobTime = Instant.now().minusSeconds(600);
        Object[] queueData = new Object[]{"DEFAULT", 15L, oldJobTime};

        when(jobRepository.getQueueStatisticsNative())
            .thenReturn(Collections.singletonList(queueData));

        maintenanceService.updateQueueMetrics();

        var ageGauge = meterRegistry.find("jobs.queue.oldest.age")
            .tag("queue", "DEFAULT")
            .gauge();
        assertNotNull(ageGauge);
        assertTrue(ageGauge.value() >= 600);
    }

    @Test
    void testUpdateQueueMetricsWithNullOldestJob() {
        Object[] queueData = new Object[]{"DEFAULT", 5L, null};

        when(jobRepository.getQueueStatisticsNative())
            .thenReturn(Collections.singletonList(queueData));

        assertDoesNotThrow(() -> maintenanceService.updateQueueMetrics());

        var depthGauge = meterRegistry.find("jobs.queue.depth")
            .tag("queue", "DEFAULT")
            .gauge();
        assertNotNull(depthGauge);
        assertEquals(5.0, depthGauge.value());
    }

    @Test
    void testUpdateQueueMetricsUpdatesExistingGauges() {
        Object[] queueData1 = new Object[]{"DEFAULT", 10L, Instant.now()};
        Object[] queueData2 = new Object[]{"DEFAULT", 20L, Instant.now()};

        when(jobRepository.getQueueStatisticsNative())
            .thenReturn(Collections.singletonList(queueData1));

        maintenanceService.updateQueueMetrics();

        var depthGauge = meterRegistry.find("jobs.queue.depth")
            .tag("queue", "DEFAULT")
            .gauge();
        assertEquals(10.0, depthGauge.value());

        when(jobRepository.getQueueStatisticsNative())
            .thenReturn(Collections.singletonList(queueData2));

        maintenanceService.updateQueueMetrics();

        assertEquals(20.0, depthGauge.value());
    }

    @Test
    void testReapZombieJobsRecordsMetricsPerQueue() {
        JobEntity zombie1 = createZombieJob("Job1");
        zombie1.setQueueName("QUEUE_A");
        JobEntity zombie2 = createZombieJob("Job2");
        zombie2.setQueueName("QUEUE_B");

        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(Arrays.asList(zombie1, zombie2));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        maintenanceService.reapZombieJobs();

        verify(metricsService).recordZombieJobReaped("QUEUE_A");
        verify(metricsService).recordZombieJobReaped("QUEUE_B");
    }

    @Test
    void testReapZombieJobsIsolatesIndividualJobFailures() {
        // Create three zombie jobs
        JobEntity zombie1 = createZombieJob("Job1");
        JobEntity zombie2 = createZombieJob("Job2");
        JobEntity zombie3 = createZombieJob("Job3");
        List<JobEntity> zombies = Arrays.asList(zombie1, zombie2, zombie3);

        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(zombies);
        
        // Mock save() to throw exception for the first job only
        when(jobRepository.save(zombie1))
            .thenThrow(new RuntimeException("Database error for job1"));
        when(jobRepository.save(zombie2)).thenAnswer(i -> i.getArgument(0));
        when(jobRepository.save(zombie3)).thenAnswer(i -> i.getArgument(0));

        maintenanceService.reapZombieJobs();

        // Verify that save() was still called for all three jobs
        verify(jobRepository).save(zombie1);
        verify(jobRepository).save(zombie2);
        verify(jobRepository).save(zombie3);
        
        // Note: All zombies are set to QUEUED in memory before save is called.
        // Even though zombie1's save fails, the in-memory object was already modified.
        // The key validation is that saves for zombie2 and zombie3 were attempted despite zombie1's failure.
        assertEquals(JobStatus.QUEUED, zombie1.getStatus());
        assertEquals(JobStatus.QUEUED, zombie2.getStatus());
        assertEquals(JobStatus.QUEUED, zombie3.getStatus());
    }

    @Test
    void testReapZombieJobsPreservesExistingErrorHistory() {
        JobEntity zombie = createZombieJob("Job1");
        String originalError = "NullPointerException: Failed to process task";
        zombie.setLastError(originalError);
        
        when(jobRepository.findZombieJobs(eq(JobStatus.PROCESSING), any(Instant.class)))
            .thenReturn(Collections.singletonList(zombie));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        maintenanceService.reapZombieJobs();

        // Verify that the error message contains both the original error and the timeout message
        String expectedError = originalError + " | Job timed out - no heartbeat update";
        assertEquals(expectedError, zombie.getLastError());
        assertTrue(zombie.getLastError().contains(originalError));
        assertTrue(zombie.getLastError().contains("timed out"));
        
        verify(jobRepository).save(zombie);
    }

    private JobEntity createZombieJob(String jobType) {
        JobEntity entity = new JobEntity("DEFAULT", jobType, "{}");
        entity.setId(UUID.randomUUID());
        entity.setStatus(JobStatus.PROCESSING);
        entity.setLastHeartbeat(Instant.now().minusSeconds(600));
        return entity;
    }
}
