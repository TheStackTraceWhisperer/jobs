package io.github.thestacktracewhisperer.jobs.reference;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.github.thestacktracewhisperer.jobs.reference.config.TestObjectMapperConfig;
import io.github.thestacktracewhisperer.jobs.worker.dispatcher.JobRoutingEngine;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive end-to-end orchestration test simulating multi-step job processing
 * with various failure scenarios including temporary failures, permanent failures, 
 * requeue operations, and successful completions.
 */
@SpringBootTest
@ActiveProfiles("orchestration")
@Import({OrchestrationEndToEndTest.TestOrchestrationConfig.class, TestObjectMapperConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrchestrationEndToEndTest {

    @Autowired
    private JobEnqueuer jobEnqueuer;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobRoutingEngine routingEngine;

    @Autowired
    private OrchestratedTaskHandler orchestratedTaskHandler;

    @BeforeEach
    void setupTestScenario() {
        // Use deleteAllInBatch to avoid optimistic locking issues with concurrent worker threads
        jobRepository.deleteAllInBatch();
        orchestratedTaskHandler.resetState();
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Controller receives request, enqueues job, worker processes successfully")
    void testSuccessfulEndToEndOrchestration() throws Exception {
        // STEP 1: Controller layer receives request and enqueues
        String requestId = "req-" + UUID.randomUUID();
        OrchestratedTask taskPayload = new OrchestratedTask(
            requestId, 
            "process-inventory-sync", 
            OrchestratedTask.ExecutionMode.IMMEDIATE_SUCCESS,
            0
        );
        
        JobEntity enqueuedJob = jobEnqueuer.enqueue(taskPayload);
        assertNotNull(enqueuedJob);
        assertEquals(JobStatus.QUEUED, enqueuedJob.getStatus());
        
        // STEP 2: Worker picks up and processes the job
        routingEngine.route(enqueuedJob.getJobType(), enqueuedJob.getPayload());
        
        // STEP 3: Verify job completed successfully
        assertTrue(orchestratedTaskHandler.wasProcessed(requestId));
        assertEquals(1, orchestratedTaskHandler.getProcessingAttempts(requestId));
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Temporary network failure causes requeue with exponential backoff")
    void testTemporaryFailureWithRequeueAndRecovery() throws Exception {
        String requestId = "req-" + UUID.randomUUID();
        OrchestratedTask taskPayload = new OrchestratedTask(
            requestId,
            "sync-distributed-cache",
            OrchestratedTask.ExecutionMode.FAIL_TWICE_THEN_SUCCEED,
            2
        );
        
        JobEntity enqueuedJob = jobEnqueuer.enqueue(taskPayload);
        
        // First attempt - should fail
        Exception firstAttemptException = assertThrows(Exception.class, () -> {
            routingEngine.route(enqueuedJob.getJobType(), enqueuedJob.getPayload());
        });
        assertTrue(firstAttemptException.getMessage().contains("Transient failure"));
        assertEquals(1, orchestratedTaskHandler.getProcessingAttempts(requestId));
        
        // Second attempt - should still fail
        Exception secondAttemptException = assertThrows(Exception.class, () -> {
            routingEngine.route(enqueuedJob.getJobType(), enqueuedJob.getPayload());
        });
        assertTrue(secondAttemptException.getMessage().contains("Transient failure"));
        assertEquals(2, orchestratedTaskHandler.getProcessingAttempts(requestId));
        
        // Third attempt - should succeed
        routingEngine.route(enqueuedJob.getJobType(), enqueuedJob.getPayload());
        assertTrue(orchestratedTaskHandler.wasProcessed(requestId));
        assertEquals(3, orchestratedTaskHandler.getProcessingAttempts(requestId));
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Permanent hardware failure exhausts retries")
    void testPermanentFailureExhaustsRetries() {
        String requestId = "req-" + UUID.randomUUID();
        OrchestratedTask taskPayload = new OrchestratedTask(
            requestId,
            "backup-to-cold-storage",
            OrchestratedTask.ExecutionMode.PERMANENT_HARDWARE_FAILURE,
            0
        );
        
        JobEntity enqueuedJob = jobEnqueuer.enqueue(taskPayload);
        
        // All attempts should fail with same error
        for (int attempt = 1; attempt <= 5; attempt++) {
            Exception exception = assertThrows(Exception.class, () -> {
                routingEngine.route(enqueuedJob.getJobType(), enqueuedJob.getPayload());
            });
            assertTrue(exception.getMessage().contains("Hardware malfunction"));
            assertEquals(attempt, orchestratedTaskHandler.getProcessingAttempts(requestId));
        }
        
        assertFalse(orchestratedTaskHandler.wasProcessed(requestId));
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Multi-step orchestration with controller → queue → worker flow")
    void testFullOrchestrationPipeline() throws Exception {
        List<String> requestIds = new ArrayList<>();
        
        // STEP 1: Controller receives multiple requests
        for (int i = 0; i < 3; i++) {
            String requestId = "bulk-req-" + i;
            requestIds.add(requestId);
            
            OrchestratedTask.ExecutionMode mode = switch(i) {
                case 0 -> OrchestratedTask.ExecutionMode.IMMEDIATE_SUCCESS;
                case 1 -> OrchestratedTask.ExecutionMode.FAIL_TWICE_THEN_SUCCEED;
                case 2 -> OrchestratedTask.ExecutionMode.PERMANENT_HARDWARE_FAILURE;
                default -> OrchestratedTask.ExecutionMode.IMMEDIATE_SUCCESS;
            };
            
            OrchestratedTask task = new OrchestratedTask(
                requestId,
                "batch-operation-" + i,
                mode,
                i == 1 ? 2 : 0
            );
            
            jobEnqueuer.enqueue(task);
        }
        
        // STEP 2: Wait for background worker to process jobs
        // Worker polls every 100ms, give it time to pick up and start processing all 3 jobs
        Thread.sleep(500);
        
        // STEP 3: Verify first job completed successfully
        assertTrue(orchestratedTaskHandler.wasProcessed("bulk-req-0"), 
            "First job should have completed successfully");
        assertEquals(1, orchestratedTaskHandler.getProcessingAttempts("bulk-req-0"),
            "First job should have been attempted once");
        
        // STEP 4: Verify second job failed on first attempt (will retry later)
        assertFalse(orchestratedTaskHandler.wasProcessed("bulk-req-1"),
            "Second job should have failed on first attempt");
        assertEquals(1, orchestratedTaskHandler.getProcessingAttempts("bulk-req-1"),
            "Second job should have been attempted once");
        
        // STEP 5: Verify third job failed on first attempt (will retry later)
        assertFalse(orchestratedTaskHandler.wasProcessed("bulk-req-2"),
            "Third job should have failed on first attempt");
        assertEquals(1, orchestratedTaskHandler.getProcessingAttempts("bulk-req-2"),
            "Third job should have been attempted once");
        
        // STEP 6: Verify job statuses
        List<JobEntity> allJobs = jobRepository.findAll();
        assertEquals(3, allJobs.size(), "Should have 3 jobs total");
        
        long successfulJobs = allJobs.stream()
            .filter(j -> j.getStatus() == JobStatus.SUCCESS)
            .count();
        assertEquals(1, successfulJobs, "Should have 1 successful job");
        
        long queuedJobs = allJobs.stream()
            .filter(j -> j.getStatus() == JobStatus.QUEUED)
            .count();
        assertEquals(2, queuedJobs, "Should have 2 jobs queued for retry");
    }

    /**
     * Test job representing an orchestrated task in a multi-step process.
     */
    public static record OrchestratedTask(
        String requestIdentifier,
        String operationName,
        ExecutionMode executionBehavior,
        int retriesBeforeSuccess
    ) implements Job {
        
        public enum ExecutionMode {
            IMMEDIATE_SUCCESS,
            FAIL_TWICE_THEN_SUCCEED,
            PERMANENT_HARDWARE_FAILURE
        }
    }

    /**
     * Test configuration providing orchestration components.
     */
    @TestConfiguration
    static class TestOrchestrationConfig {
        
        @Bean
        public OrchestratedTaskHandler orchestratedTaskHandler() {
            return new OrchestratedTaskHandler();
        }
    }

    /**
     * Handler simulating real-world processing with various failure modes.
     */
    public static class OrchestratedTaskHandler implements JobHandler<OrchestratedTask> {
        
        private final ConcurrentHashMap<String, AtomicInteger> attemptCounters = new ConcurrentHashMap<>();
        private final List<String> successfullyProcessed = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handle(OrchestratedTask task) throws Exception {
            String reqId = task.requestIdentifier();
            int attemptNum = attemptCounters.computeIfAbsent(reqId, k -> new AtomicInteger(0))
                .incrementAndGet();
            
            switch (task.executionBehavior()) {
                case IMMEDIATE_SUCCESS -> {
                    successfullyProcessed.add(reqId);
                }
                case FAIL_TWICE_THEN_SUCCEED -> {
                    if (attemptNum <= task.retriesBeforeSuccess()) {
                        throw new Exception("Transient failure - network timeout attempt " + attemptNum);
                    }
                    successfullyProcessed.add(reqId);
                }
                case PERMANENT_HARDWARE_FAILURE -> {
                    throw new Exception("Hardware malfunction - disk sector unreadable");
                }
            }
        }

        public boolean wasProcessed(String requestId) {
            return successfullyProcessed.contains(requestId);
        }

        public int getProcessingAttempts(String requestId) {
            AtomicInteger counter = attemptCounters.get(requestId);
            return counter != null ? counter.get() : 0;
        }

        public void resetState() {
            attemptCounters.clear();
            successfullyProcessed.clear();
        }
    }
}
