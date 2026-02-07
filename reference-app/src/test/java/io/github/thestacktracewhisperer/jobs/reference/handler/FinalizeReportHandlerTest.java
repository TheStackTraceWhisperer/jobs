package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalizeReportHandlerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private JobRepository jobRepository;

    private FinalizeReportHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FinalizeReportHandler(reportService, jobRepository);
    }

    @Test
    void testHandleWithNoPendingTasks() throws Exception {
        // Arrange
        UUID reportId = UUID.randomUUID();
        FinalizeReportJob job = new FinalizeReportJob(reportId);

        when(jobRepository.countByStatusAndTraceId(JobStatus.QUEUED, reportId))
            .thenReturn(0L);

        // Act
        handler.handle(job);

        // Assert
        verify(jobRepository).countByStatusAndTraceId(JobStatus.QUEUED, reportId);
        verify(reportService).finalizeReport(reportId);
    }

    @Test
    void testHandleWithPendingTasksThrowsSnoozeException() throws Exception {
        // Arrange
        UUID reportId = UUID.randomUUID();
        FinalizeReportJob job = new FinalizeReportJob(reportId);

        when(jobRepository.countByStatusAndTraceId(JobStatus.QUEUED, reportId))
            .thenReturn(5L);

        // Act & Assert
        JobSnoozeException exception = assertThrows(JobSnoozeException.class, () -> {
            handler.handle(job);
        });

        // Verify exception properties
        assertNotNull(exception.getMessage());
        assertEquals("Waiting for sub-tasks", exception.getMessage());
        assertEquals(30, exception.getDelay().getSeconds());

        // Verify interactions
        verify(jobRepository).countByStatusAndTraceId(JobStatus.QUEUED, reportId);
        verify(reportService, never()).finalizeReport(any());
    }

    @Test
    void testHandleWithOnePendingTaskThrowsSnoozeException() throws Exception {
        // Arrange
        UUID reportId = UUID.randomUUID();
        FinalizeReportJob job = new FinalizeReportJob(reportId);

        when(jobRepository.countByStatusAndTraceId(JobStatus.QUEUED, reportId))
            .thenReturn(1L);

        // Act & Assert
        assertThrows(JobSnoozeException.class, () -> {
            handler.handle(job);
        });

        verify(reportService, never()).finalizeReport(any());
    }

    @Test
    void testHandleCallsRepositoryWithCorrectParameters() throws Exception {
        // Arrange
        UUID reportId = UUID.randomUUID();
        FinalizeReportJob job = new FinalizeReportJob(reportId);

        when(jobRepository.countByStatusAndTraceId(JobStatus.QUEUED, reportId))
            .thenReturn(0L);

        // Act
        handler.handle(job);

        // Assert - verify exact parameters
        verify(jobRepository).countByStatusAndTraceId(JobStatus.QUEUED, reportId);
        verify(reportService).finalizeReport(reportId);
    }
}
