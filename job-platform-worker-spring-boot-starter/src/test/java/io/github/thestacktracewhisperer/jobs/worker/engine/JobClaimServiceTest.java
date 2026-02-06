package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobClaimServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobClaimService jobClaimService;

    @BeforeEach
    void setUp() {
        jobClaimService = new JobClaimService(jobRepository);
    }

    @Test
    void testFetchAndClaimJobsSuccessfully() {
        Collection<String> supportedTypes = Arrays.asList("TestJob", "OtherJob");
        String queueName = "DEFAULT";
        int batchSize = 5;

        JobEntity job1 = createJobEntity("TestJob");
        JobEntity job2 = createJobEntity("OtherJob");
        List<JobEntity> jobs = Arrays.asList(job1, job2);
        Page<JobEntity> page = new PageImpl<>(jobs);

        when(jobRepository.findJobsReadyForProcessing(
            eq(JobStatus.QUEUED), eq(queueName), any(Instant.class), 
            eq(supportedTypes), any(Pageable.class)
        )).thenReturn(page);

        when(jobRepository.findById(job1.getId())).thenReturn(Optional.of(job1));
        when(jobRepository.findById(job2.getId())).thenReturn(Optional.of(job2));
        when(jobRepository.saveAndFlush(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        List<JobEntity> result = jobClaimService.fetchAndClaimJobs(supportedTypes, queueName, batchSize);

        assertEquals(2, result.size());
        
        verify(jobRepository, times(2)).saveAndFlush(any(JobEntity.class));
        
        assertEquals(1, job1.getAttempts());
        assertEquals(JobStatus.PROCESSING, job1.getStatus());
        assertNotNull(job1.getLastHeartbeat());
    }

    @Test
    void testFetchAndClaimJobsWithEmptySupportedTypes() {
        Collection<String> supportedTypes = Collections.emptyList();
        
        List<JobEntity> result = jobClaimService.fetchAndClaimJobs(supportedTypes, "DEFAULT", 5);

        assertTrue(result.isEmpty());
        verify(jobRepository, never()).findJobsReadyForProcessing(any(), any(), any(), any(), any());
    }

    @Test
    void testFetchAndClaimJobsWithNoAvailableJobs() {
        Collection<String> supportedTypes = Arrays.asList("TestJob");
        Page<JobEntity> emptyPage = new PageImpl<>(Collections.emptyList());

        when(jobRepository.findJobsReadyForProcessing(
            any(), any(), any(), any(), any()
        )).thenReturn(emptyPage);

        List<JobEntity> result = jobClaimService.fetchAndClaimJobs(supportedTypes, "DEFAULT", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchAndClaimJobsHandlesOptimisticLockingFailure() {
        Collection<String> supportedTypes = Arrays.asList("TestJob");
        JobEntity job = createJobEntity("TestJob");
        Page<JobEntity> page = new PageImpl<>(Collections.singletonList(job));

        when(jobRepository.findJobsReadyForProcessing(
            any(), any(), any(), any(), any()
        )).thenReturn(page);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.saveAndFlush(any(JobEntity.class)))
            .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("test", null));

        List<JobEntity> result = jobClaimService.fetchAndClaimJobs(supportedTypes, "DEFAULT", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchAndClaimJobsSkipsAlreadyClaimedJob() {
        Collection<String> supportedTypes = Arrays.asList("TestJob");
        JobEntity job = createJobEntity("TestJob");
        job.setStatus(JobStatus.PROCESSING); // Already claimed
        Page<JobEntity> page = new PageImpl<>(Collections.singletonList(job));

        when(jobRepository.findJobsReadyForProcessing(
            any(), any(), any(), any(), any()
        )).thenReturn(page);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        List<JobEntity> result = jobClaimService.fetchAndClaimJobs(supportedTypes, "DEFAULT", 5);

        assertTrue(result.isEmpty());
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void testMarkJobSuccess() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        jobClaimService.markJobSuccess(jobId);

        assertEquals(JobStatus.SUCCESS, job.getStatus());
        verify(jobRepository).save(job);
    }

    @Test
    void testMarkJobSuccessThrowsExceptionWhenJobNotFound() {
        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> {
            jobClaimService.markJobSuccess(jobId);
        });
    }

    @Test
    void testHandleJobFailureWithRetry() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(1);
        String errorMessage = "Test error";
        int maxAttempts = 3;

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        jobClaimService.handleJobFailure(jobId, errorMessage, maxAttempts);

        assertEquals(JobStatus.QUEUED, job.getStatus());
        assertEquals(errorMessage, job.getLastError());
        assertNotNull(job.getRunAt());
        assertTrue(job.getRunAt().isAfter(Instant.now()));
    }

    @Test
    void testHandleJobFailurePermanent() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(3);
        String errorMessage = "Permanent error";
        int maxAttempts = 3;

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        jobClaimService.handleJobFailure(jobId, errorMessage, maxAttempts);

        assertEquals(JobStatus.PERMANENTLY_FAILED, job.getStatus());
        assertEquals(errorMessage, job.getLastError());
    }

    @Test
    void testHandleJobFailureExponentialBackoff() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(2);
        int maxAttempts = 5;

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        Instant before = Instant.now();
        jobClaimService.handleJobFailure(jobId, "error", maxAttempts);

        // 2^2 = 4 minutes
        Instant expectedRunAt = before.plus(Duration.ofMinutes(4));
        assertTrue(job.getRunAt().isAfter(before));
        assertTrue(job.getRunAt().isBefore(expectedRunAt.plus(Duration.ofSeconds(5))));
    }

    @Test
    void testHandleJobSnooze() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(2);
        Duration delay = Duration.ofMinutes(10);
        JobSnoozeException snooze = new JobSnoozeException("Snoozing", delay);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        Instant before = Instant.now();
        jobClaimService.handleJobSnooze(jobId, snooze);

        assertEquals(JobStatus.QUEUED, job.getStatus());
        assertEquals(1, job.getAttempts()); // Refunded from 2 to 1
        assertTrue(job.getRunAt().isAfter(before));
        assertTrue(job.getLastError().contains("Snoozed"));
    }

    @Test
    void testHandleJobSnoozeRefundsAttemptToZero() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(1);
        Duration delay = Duration.ofMinutes(5);
        JobSnoozeException snooze = new JobSnoozeException("Snoozing", delay);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        jobClaimService.handleJobSnooze(jobId, snooze);

        assertEquals(0, job.getAttempts()); // Refunded from 1 to 0
    }

    @Test
    void testHandleJobSnoozeDoesNotGoNegative() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity("TestJob");
        job.setId(jobId);
        job.setAttempts(0);
        Duration delay = Duration.ofMinutes(5);
        JobSnoozeException snooze = new JobSnoozeException("Snoozing", delay);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(i -> i.getArgument(0));

        jobClaimService.handleJobSnooze(jobId, snooze);

        assertEquals(0, job.getAttempts()); // Stays at 0
    }

    private JobEntity createJobEntity(String jobType) {
        JobEntity entity = new JobEntity("DEFAULT", jobType, "{}");
        entity.setId(UUID.randomUUID());
        entity.setStatus(JobStatus.QUEUED);
        return entity;
    }
}
