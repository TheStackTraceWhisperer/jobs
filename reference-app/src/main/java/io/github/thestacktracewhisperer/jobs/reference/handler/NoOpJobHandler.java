package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.common.handler.JobHandler;
import io.github.thestacktracewhisperer.jobs.reference.job.NoOpJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for no-op jobs used in performance testing.
 * This handler does nothing except log and optionally fail,
 * allowing us to measure pure job processing overhead.
 */
@Component
@Slf4j
public class NoOpJobHandler implements JobHandler<NoOpJob> {

    @Override
    public void handle(NoOpJob job) throws Exception {
        log.trace("Processing no-op job: {}", job.jobId());
        
        if (job.shouldFail()) {
            throw new RuntimeException("Intentional failure for testing: " + job.jobId());
        }
        
        // No-op: return immediately
    }
}
