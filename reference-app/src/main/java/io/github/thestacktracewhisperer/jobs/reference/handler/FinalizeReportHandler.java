package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSnoozeException;
import io.github.thestacktracewhisperer.jobs.producer.context.JobContextHolder;
import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Handler for finalizing reports after all user reports are complete.
 * Demonstrates the job snooze pattern by waiting for sibling jobs to complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeReportHandler implements JobHandler<FinalizeReportJob> {

    private final ReportService reportService;
    private final JobRepository jobRepository;

    @Override
    public void handle(FinalizeReportJob job) throws Exception {
        log.info("Finalizing report: reportId={}", job.reportId());
        
        // Get the current job's ID to find sibling jobs
        UUID currentJobId = JobContextHolder.getCurrentJobId();
        if (currentJobId == null) {
            log.warn("No current job ID found in context, skipping sibling job check");
            reportService.finalizeReport(job.reportId());
            return;
        }
        
        // Get the current job entity to find its parent
        var currentJobEntity = jobRepository.findById(currentJobId)
            .orElseThrow(() -> new IllegalStateException("Current job not found: " + currentJobId));
        
        UUID parentJobId = currentJobEntity.getParentJobId();
        if (parentJobId == null) {
            log.warn("No parent job ID found, skipping sibling job check");
            reportService.finalizeReport(job.reportId());
            return;
        }
        
        // Count pending sibling jobs (same parent, but not this finalization job)
        // Check for both QUEUED and PROCESSING status
        long queuedSiblings = jobRepository.countByStatusAndParentJobId(JobStatus.QUEUED, parentJobId);
        long processingSiblings = jobRepository.countByStatusAndParentJobId(JobStatus.PROCESSING, parentJobId);
        
        // Subtract 1 from PROCESSING count if this job is still in PROCESSING status
        long pending = queuedSiblings + processingSiblings - 1;
        
        if (pending > 0) {
            // Defer execution for 30 seconds without incrementing retry counter
            log.info("Waiting for {} sibling job(s) to complete before finalizing report: reportId={}", 
                pending, job.reportId());
            throw new JobSnoozeException(
                "Waiting for " + pending + " user report(s) to complete",
                Duration.ofSeconds(30)
            );
        }
        
        // All sibling jobs are complete, proceed with finalization
        log.info("All user reports complete, finalizing report: reportId={}", job.reportId());
        reportService.finalizeReport(job.reportId());
        
        log.info("Report finalized: reportId={}", job.reportId());
    }
}
