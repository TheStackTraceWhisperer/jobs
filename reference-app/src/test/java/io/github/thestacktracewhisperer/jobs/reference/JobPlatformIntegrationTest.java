package io.github.thestacktracewhisperer.jobs.reference;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.reference.job.HelloWorldJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the job platform using H2 in-memory database.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobPlatformIntegrationTest {

    @Autowired
    private JobEnqueuer jobEnqueuer;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void testEnqueueJob() {
        // Enqueue a simple job
        HelloWorldJob job = new HelloWorldJob("Test message");
        JobEntity entity = jobEnqueuer.enqueue(job);

        // Verify it was persisted
        assertNotNull(entity);
        assertNotNull(entity.getId());
        assertEquals(JobStatus.QUEUED, entity.getStatus());
        assertEquals("DEFAULT", entity.getQueueName());
        assertEquals(HelloWorldJob.class.getName(), entity.getJobType());
        assertNotNull(entity.getPayload());

        // Verify we can retrieve it
        JobEntity retrieved = jobRepository.findById(entity.getId()).orElse(null);
        assertNotNull(retrieved);
        assertEquals(entity.getId(), retrieved.getId());
    }

    @Test
    void testJobCount() {
        // Clear existing jobs
        jobRepository.deleteAll();

        // Enqueue multiple jobs
        jobEnqueuer.enqueue(new HelloWorldJob("Message 1"));
        jobEnqueuer.enqueue(new HelloWorldJob("Message 2"));
        jobEnqueuer.enqueue(new HelloWorldJob("Message 3"));

        // Verify count
        long count = jobRepository.countByStatus(JobStatus.QUEUED);
        assertEquals(3, count);
    }
}
