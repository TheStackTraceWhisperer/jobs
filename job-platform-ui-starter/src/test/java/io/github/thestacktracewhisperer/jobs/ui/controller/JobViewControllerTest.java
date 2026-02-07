package io.github.thestacktracewhisperer.jobs.ui.controller;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.ui.exception.JobNotFoundException;
import io.github.thestacktracewhisperer.jobs.ui.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobViewController.class)
@ContextConfiguration(classes = {JobViewController.class, JobViewControllerTest.TestConfig.class})
class JobViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobService jobService;

    private JobEntity testJob;

    @BeforeEach
    void setUp() {
        testJob = new JobEntity("DEFAULT", "TestJob", "{\"data\":\"test\"}");
        testJob.setId(UUID.randomUUID());
        testJob.setStatus(JobStatus.QUEUED);
    }

    @Test
    void listJobs_shouldReturnJobsListView() throws Exception {
        Page<JobEntity> page = new PageImpl<>(List.of(testJob));
        when(jobService.findJobs(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs-list"))
                .andExpect(model().attributeExists("jobs"))
                .andExpect(model().attributeExists("statuses"))
                .andExpect(model().attribute("totalElements", 1L));
    }

    @Test
    void listJobs_withFilters_shouldPassFiltersToService() throws Exception {
        Page<JobEntity> page = new PageImpl<>(List.of(testJob));
        when(jobService.findJobs(eq(JobStatus.QUEUED), eq("DEFAULT"), eq("Test"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/jobs")
                        .param("status", "QUEUED")
                        .param("queueName", "DEFAULT")
                        .param("jobType", "Test"))
                .andExpect(status().isOk())
                .andExpect(view().name("jobs-list"))
                .andExpect(model().attribute("selectedStatus", JobStatus.QUEUED))
                .andExpect(model().attribute("selectedQueueName", "DEFAULT"))
                .andExpect(model().attribute("selectedJobType", "Test"));
    }

    @Test
    void jobDetails_whenJobExists_shouldReturnDetailsView() throws Exception {
        UUID id = testJob.getId();
        when(jobService.findById(id)).thenReturn(Optional.of(testJob));

        mockMvc.perform(get("/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"))
                .andExpect(model().attributeExists("job"))
                .andExpect(model().attribute("job", testJob));
    }

    @Test
    void jobDetails_whenJobNotFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/jobs/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Configuration
    static class TestConfig {
        @Bean
        public JobService jobService() {
            return mock(JobService.class);
        }
    }
}
