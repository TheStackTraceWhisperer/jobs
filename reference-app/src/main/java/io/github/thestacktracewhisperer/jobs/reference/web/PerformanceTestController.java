package io.github.thestacktracewhisperer.jobs.reference.web;

import io.github.thestacktracewhisperer.jobs.reference.job.NoOpJob;
import io.github.thestacktracewhisperer.jobs.producer.JobEnqueuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for performance testing.
 * Only enabled in 'api' profile.
 */
@RestController
@RequestMapping("/api/perf-test")
@RequiredArgsConstructor
@Slf4j
@Profile("api")
public class PerformanceTestController {

    private final JobEnqueuer jobEnqueuer;

    /**
     * Enqueue a no-op job for performance testing.
     * The job does nothing but return immediately, allowing us to measure
     * throughput and drain rate of the job processing system.
     *
     * @return Job ID that was enqueued
     */
    @PostMapping("/enqueue-noop")
    public ResponseEntity<Map<String, String>> enqueueNoOpJob() {
        String jobId = UUID.randomUUID().toString();
        jobEnqueuer.enqueue(new NoOpJob(jobId));
        log.debug("Enqueued no-op job: {}", jobId);
        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobId, "status", "ENQUEUED"));
    }

    /**
     * Enqueue a batch of no-op jobs for performance testing.
     *
     * @param count Number of jobs to enqueue (default 1, max 1000)
     * @return Number of jobs enqueued
     */
    @PostMapping("/enqueue-noop-batch")
    public ResponseEntity<Map<String, Object>> enqueueNoOpJobBatch(
            @RequestParam(defaultValue = "1") int count) {
        
        if (count < 1 || count > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Count must be between 1 and 1000"));
        }

        for (int i = 0; i < count; i++) {
            String jobId = UUID.randomUUID().toString();
            jobEnqueuer.enqueue(new NoOpJob(jobId));
        }

        log.info("Enqueued {} no-op jobs", count);
        return ResponseEntity.accepted()
                .body(Map.of("count", count, "status", "ENQUEUED"));
    }

    /**
     * Enqueue a job that will fail for testing error handling.
     *
     * @return Job ID that was enqueued
     */
    @PostMapping("/enqueue-failing-job")
    public ResponseEntity<Map<String, String>> enqueueFailingJob() {
        String jobId = UUID.randomUUID().toString();
        jobEnqueuer.enqueue(new NoOpJob(jobId, true)); // Will fail
        log.debug("Enqueued failing job: {}", jobId);
        return ResponseEntity.accepted()
                .body(Map.of("jobId", jobId, "status", "ENQUEUED", "willFail", "true"));
    }
}
