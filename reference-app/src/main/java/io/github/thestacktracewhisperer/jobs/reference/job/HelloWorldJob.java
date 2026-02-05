package io.github.thestacktracewhisperer.jobs.reference.job;

import io.github.thestacktracewhisperer.jobs.common.model.Job;

/**
 * Simple example job for testing.
 */
public record HelloWorldJob(String message) implements Job {
}
