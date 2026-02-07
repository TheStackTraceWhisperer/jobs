package io.github.thestacktracewhisperer.jobs.ui.controller;

import io.github.thestacktracewhisperer.jobs.ui.exception.JobNotFoundException;
import io.github.thestacktracewhisperer.jobs.ui.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobManagementController.class)
@ContextConfiguration(classes = {JobManagementController.class, JobManagementControllerTest.TestConfig.class})
class JobManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobService jobService;

    @Test
    void requeue_shouldCallServiceAndReturnOk() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/jobs/{id}/requeue", id))
                .andExpect(status().isOk());

        verify(jobService).requeue(id);
    }

    @Test
    void requeue_whenJobNotFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new JobNotFoundException("Job not found: " + id))
                .when(jobService).requeue(id);

        mockMvc.perform(post("/api/jobs/{id}/requeue", id))
                .andExpect(status().isNotFound());

        verify(jobService).requeue(id);
    }

    @Test
    void cancel_shouldCallServiceAndReturnOk() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/jobs/{id}/cancel", id))
                .andExpect(status().isOk());

        verify(jobService).cancel(id);
    }

    @Test
    void cancel_whenJobNotFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new JobNotFoundException("Job not found: " + id))
                .when(jobService).cancel(id);

        mockMvc.perform(post("/api/jobs/{id}/cancel", id))
                .andExpect(status().isNotFound());

        verify(jobService).cancel(id);
    }

    @Test
    void delete_shouldCallServiceAndReturnOk() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/jobs/{id}", id))
                .andExpect(status().isOk());

        verify(jobService).delete(id);
    }

    @Configuration
    static class TestConfig {
        @Bean
        public JobService jobService() {
            return mock(JobService.class);
        }
    }
}
