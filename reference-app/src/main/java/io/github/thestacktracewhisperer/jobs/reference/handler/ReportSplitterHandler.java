package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.reference.job.FinalizeReportJob;
import io.github.thestacktracewhisperer.jobs.reference.job.GenerateMonthlyReportJob;
import io.github.thestacktracewhisperer.jobs.reference.job.UserReportJob;
import io.github.thestacktracewhisperer.jobs.reference.repository.UserRepository;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * Handler that demonstrates the fan-out pattern.
 * Splits a monthly report into individual user reports.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportSplitterHandler implements JobHandler<GenerateMonthlyReportJob> {

    private final JobEnqueuer enqueuer;
    private final UserRepository userRepository;

    @Override
    public void handle(GenerateMonthlyReportJob job) throws Exception {
        log.info("Splitting monthly report into user reports: reportId={}, year={}, month={}", 
            job.reportId(), job.year(), job.month());

        // Stream users to avoid loading all into memory
        try (Stream<java.util.UUID> userStream = userRepository.streamActiveUserIds()) {
            userStream.forEach(userId -> {
                enqueuer.enqueue(new UserReportJob(job.reportId(), userId));
            });
        }

        // Enqueue finalization job
        enqueuer.enqueue(new FinalizeReportJob(job.reportId()));

        log.info("Monthly report split complete: reportId={}", job.reportId());
    }
}
