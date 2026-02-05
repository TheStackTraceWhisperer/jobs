package io.github.thestacktracewhisperer.jobs.common.model;

/**
 * Marker interface for fan-out jobs that spawn multiple child jobs.
 */
public non-sealed interface FanOutJob extends Job {
}
