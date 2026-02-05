package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.SimpleJob;

/**
 * Simple example job for testing.
 */
public record HelloWorldJob(String message) implements SimpleJob {
}
