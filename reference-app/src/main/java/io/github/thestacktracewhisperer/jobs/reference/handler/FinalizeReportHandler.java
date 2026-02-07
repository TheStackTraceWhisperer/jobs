package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Handler for finalizing reports after all user reports are complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeReportHandler implements JobHandler<FinalizeReportJob> {

    private final ReportService reportService;
    private final JobRepository jobRepository;

    @Override
    public void handle(FinalizeReportJob job) throws Exception {
        // 1. The Guard Clause (The Logic you wanted)
        long pendingTasks = jobRepository.countByStatusAndTraceId(
            JobStatus.QUEUED, job.reportId()
        );

        if (pendingTasks > 0) {
            log.info("Report {} waiting for {} tasks...", job.reportId(), pendingTasks);
            // THROW THE SNOOZE
            throw new JobSnoozeException("Waiting for sub-tasks", Duration.ofSeconds(30));
        }

        // 2. The Actual Work
        log.info("All tasks done. Finalizing report: {}", job.reportId());
        reportService.finalizeReport(job.reportId());
    }
}
