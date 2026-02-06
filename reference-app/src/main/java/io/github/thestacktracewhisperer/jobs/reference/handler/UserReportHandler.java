package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.UserReportJob;
import io.github.thestacktracewhisperer.jobs.reference.service.ReportService;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for generating individual user reports.
 */
@Component
@RequiredArgsConstructor
public class UserReportHandler implements JobHandler<UserReportJob> {

    private static final Logger log = LoggerFactory.getLogger(UserReportHandler.class);

    private final ReportService reportService;

    @Override
    public void handle(UserReportJob job) throws Exception {
        log.info("Generating user report: reportId={}, userId={}", job.reportId(), job.userId());
        
        reportService.generateUserReport(job.reportId(), job.userId());
        
        log.info("User report generated: reportId={}, userId={}", job.reportId(), job.userId());
    }
}
