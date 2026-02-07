package io.github.thestacktracewhisperer.jobs.worker.service;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobManagementServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobManagementService jobManagementService;

    @BeforeEach
    void setUp() {
        jobManagementService = new JobManagementService(jobRepository);
    }

    // ============================================================================
    // CANCEL JOB TESTS
    // ============================================================================

    @Test
    void testCancelQueuedJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.QUEUED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(job);
        
        jobManagementService.cancelJob(jobId);
        
        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.CANCELLED, savedJob.getStatus());
    }

    @Test
    void testCancelProcessingJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.PROCESSING);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(job);
        
        jobManagementService.cancelJob(jobId);
        
        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.CANCELLED, savedJob.getStatus());
    }

    @Test
    void testCancelFailedJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.FAILED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(job);
        
        jobManagementService.cancelJob(jobId);
        
        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.CANCELLED, savedJob.getStatus());
    }

    @Test
    void testCannotCancelSuccessfulJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.SUCCESS);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.cancelJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("terminal state"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCannotCancelPermanentlyFailedJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.PERMANENTLY_FAILED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.cancelJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("terminal state"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCannotCancelAlreadyCancelledJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.CANCELLED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.cancelJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("terminal state"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCancelNonExistentJob() {
        UUID jobId = UUID.randomUUID();
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jobManagementService.cancelJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("Job not found"));
        verify(jobRepository, never()).save(any());
    }

    // ============================================================================
    // RETRY JOB TESTS
    // ============================================================================

    @Test
    void testRetryPermanentlyFailedJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.PERMANENTLY_FAILED);
        job.setAttempts(5);
        job.setLastError("Original error");
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(job);
        
        jobManagementService.retryJob(jobId);
        
        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.QUEUED, savedJob.getStatus());
        assertEquals(0, savedJob.getAttempts());
        assertNotNull(savedJob.getRunAt());
        assertTrue(savedJob.getLastError().contains("Manually retried"));
        assertTrue(savedJob.getLastError().contains("Original error"));
    }

    @Test
    void testRetryPermanentlyFailedJobWithNoError() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.PERMANENTLY_FAILED);
        job.setAttempts(5);
        job.setLastError(null);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(job);
        
        jobManagementService.retryJob(jobId);
        
        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.QUEUED, savedJob.getStatus());
        assertEquals(0, savedJob.getAttempts());
        assertEquals("Manually retried", savedJob.getLastError());
    }

    @Test
    void testCannotRetryQueuedJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.QUEUED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.retryJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("PERMANENTLY_FAILED"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCannotRetryProcessingJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.PROCESSING);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.retryJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("PERMANENTLY_FAILED"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCannotRetrySuccessfulJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.SUCCESS);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.retryJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("PERMANENTLY_FAILED"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testCannotRetryCancelledJob() {
        UUID jobId = UUID.randomUUID();
        JobEntity job = createJobEntity(jobId, JobStatus.CANCELLED);
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            jobManagementService.retryJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("PERMANENTLY_FAILED"));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void testRetryNonExistentJob() {
        UUID jobId = UUID.randomUUID();
        
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            jobManagementService.retryJob(jobId);
        });
        
        assertTrue(exception.getMessage().contains("Job not found"));
        verify(jobRepository, never()).save(any());
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private JobEntity createJobEntity(UUID id, JobStatus status) {
        JobEntity job = new JobEntity("DEFAULT", "TestJob", "{}");
        job.setId(id);
        job.setStatus(status);
        job.setRunAt(Instant.now());
        return job;
    }
}
