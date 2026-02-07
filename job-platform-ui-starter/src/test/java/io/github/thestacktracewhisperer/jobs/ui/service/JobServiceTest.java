package io.github.thestacktracewhisperer.jobs.ui.service;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.ui.exception.JobNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    private JobEntity testJob;

    @BeforeEach
    void setUp() {
        testJob = new JobEntity("DEFAULT", "TestJob", "{\"data\":\"test\"}");
        testJob.setId(UUID.randomUUID());
        testJob.setStatus(JobStatus.QUEUED);
    }

    @Test
    void findJobs_withNoFilters_shouldReturnAllJobs() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<JobEntity> expectedPage = new PageImpl<>(List.of(testJob));
        when(jobRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<JobEntity> result = jobService.findJobs(null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(jobRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findJobs_withStatusFilter_shouldFilterByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<JobEntity> expectedPage = new PageImpl<>(List.of(testJob));
        when(jobRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expectedPage);

        Page<JobEntity> result = jobService.findJobs(JobStatus.QUEUED, null, null, pageable);

        assertNotNull(result);
        verify(jobRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findById_whenJobExists_shouldReturnJob() {
        UUID id = testJob.getId();
        when(jobRepository.findById(id)).thenReturn(Optional.of(testJob));

        Optional<JobEntity> result = jobService.findById(id);

        assertTrue(result.isPresent());
        assertEquals(testJob.getId(), result.get().getId());
    }

    @Test
    void findById_whenJobNotExists_shouldReturnEmpty() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        Optional<JobEntity> result = jobService.findById(id);

        assertFalse(result.isPresent());
    }

    @Test
    void requeue_shouldUpdateJobStatusToQueued() {
        UUID id = testJob.getId();
        testJob.setStatus(JobStatus.FAILED);
        when(jobRepository.findById(id)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(testJob);

        jobService.requeue(id);

        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        JobEntity savedJob = captor.getValue();
        assertEquals(JobStatus.QUEUED, savedJob.getStatus());
        assertNotNull(savedJob.getRunAt());
    }

    @Test
    void requeue_whenJobNotFound_shouldThrowException() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.requeue(id));
    }

    @Test
    void cancel_shouldUpdateJobStatusToCancelled() {
        UUID id = testJob.getId();
        when(jobRepository.findById(id)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(JobEntity.class))).thenReturn(testJob);

        jobService.cancel(id);

        ArgumentCaptor<JobEntity> captor = ArgumentCaptor.forClass(JobEntity.class);
        verify(jobRepository).save(captor.capture());
        assertEquals(JobStatus.CANCELLED, captor.getValue().getStatus());
    }

    @Test
    void cancel_whenJobNotFound_shouldThrowException() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(JobNotFoundException.class, () -> jobService.cancel(id));
    }

    @Test
    void delete_shouldCallRepositoryDelete() {
        UUID id = testJob.getId();

        jobService.delete(id);

        verify(jobRepository).deleteById(id);
    }

    @Test
    void delete_whenJobNotFound_shouldNotThrowException() {
        UUID id = UUID.randomUUID();

        // Should not throw - delete is idempotent
        assertDoesNotThrow(() -> jobService.delete(id));
        
        verify(jobRepository).deleteById(id);
    }
}
