package io.github.thestacktracewhisperer.jobs.reference.handler;

import io.github.thestacktracewhisperer.jobs.reference.job.HelloWorldJob;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple handler for demonstration purposes.
 */
@Component
public class HelloWorldHandler implements JobHandler<HelloWorldJob> {

    private static final Logger log = LoggerFactory.getLogger(HelloWorldHandler.class);

    @Override
    public void handle(HelloWorldJob job) {
        log.info("Hello World Job executed: {}", job.message());
    }
}
