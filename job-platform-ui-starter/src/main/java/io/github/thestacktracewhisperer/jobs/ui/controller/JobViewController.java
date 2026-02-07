package io.github.thestacktracewhisperer.jobs.ui.controller;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.ui.exception.JobNotFoundException;
import io.github.thestacktracewhisperer.jobs.ui.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Controller for rendering job views.
 */
@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobViewController {

    private final JobService jobService;

    /**
     * Display list of jobs with paging and filtering.
     */
    @GetMapping
    public String listJobs(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String queueName,
            @RequestParam(required = false) String jobType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<JobEntity> jobs = jobService.findJobs(status, queueName, jobType, pageable);

        model.addAttribute("jobs", jobs);
        model.addAttribute("currentPage", page);
        model.addAttribute("currentSize", size);
        model.addAttribute("totalPages", jobs.getTotalPages());
        model.addAttribute("totalElements", jobs.getTotalElements());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedQueueName", queueName);
        model.addAttribute("selectedJobType", jobType);
        model.addAttribute("statuses", JobStatus.values());

        return "jobs-list";
    }

    /**
     * Display details of a single job.
     */
    @GetMapping("/{id}")
    public String jobDetails(@PathVariable UUID id, Model model) {
        JobEntity job = jobService.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));

        model.addAttribute("job", job);
        return "job-details";
    }
}
