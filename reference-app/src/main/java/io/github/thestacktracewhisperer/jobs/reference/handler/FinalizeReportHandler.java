package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for finalizing reports after all user reports are complete.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalizeReportHandler implements JobHandler<FinalizeReportJob> {

    private final ReportService reportService;

    @Override
    public void handle(FinalizeReportJob job) throws Exception {
        log.info("Finalizing report: reportId={}", job.reportId());
        
        reportService.finalizeReport(job.reportId());
        
        log.info("Report finalized: reportId={}", job.reportId());
    }
}
