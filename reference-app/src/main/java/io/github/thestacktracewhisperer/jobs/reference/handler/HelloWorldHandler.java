package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.HelloWorldJob;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simple handler for demonstration purposes.
 */
@Component
@Slf4j
public class HelloWorldHandler implements JobHandler<HelloWorldJob> {

    @Override
    public void handle(HelloWorldJob job) {
        log.info("Hello World Job executed: {}", job.message());
    }
}
