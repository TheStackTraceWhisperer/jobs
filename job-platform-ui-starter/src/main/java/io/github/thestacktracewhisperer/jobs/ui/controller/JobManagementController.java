package io.github.thestacktracewhisperer.jobs.ui.controller;

import io.github.thestacktracewhisperer.jobs.ui.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for job management actions (htmx endpoints).
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobManagementController {

    private final JobService jobService;

    /**
     * Requeue a job.
     */
    @PostMapping("/{id}/requeue")
    public ResponseEntity<Void> requeue(@PathVariable UUID id) {
        jobService.requeue(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Cancel a job.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        jobService.cancel(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Delete a job.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobService.delete(id);
        return ResponseEntity.ok().build();
    }
}
