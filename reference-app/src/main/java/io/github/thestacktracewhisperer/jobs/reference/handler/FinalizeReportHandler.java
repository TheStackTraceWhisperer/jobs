package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for finalizing reports after all user reports are complete.
 */
@Component
@RequiredArgsConstructor
public class FinalizeReportHandler implements JobHandler<FinalizeReportJob> {

    private static final Logger log = LoggerFactory.getLogger(FinalizeReportHandler.class);

    private final ReportService reportService;

    @Override
    public void handle(FinalizeReportJob job) throws Exception {
        log.info("Finalizing report: reportId={}", job.reportId());
        
        reportService.finalizeReport(job.reportId());
        
        log.info("Report finalized: reportId={}", job.reportId());
    }
}
