package io.github.thestacktracewhisperer.jobs.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

class JobMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private JobMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new JobMetricsService(meterRegistry);
    }

    @Test
    void testRecordJobEnqueued() {
        metricsService.recordJobEnqueued("TestJob", "DEFAULT");
        
        Counter counter = meterRegistry.find("jobs.enqueued.total")
            .tag("job_type", "TestJob")
            .tag("queue", "DEFAULT")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordJobStarted() {
        metricsService.recordJobStarted("TestJob", "DEFAULT");
        
        Counter counter = meterRegistry.find("jobs.started.total")
            .tag("job_type", "TestJob")
            .tag("queue", "DEFAULT")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordJobCompleted() {
        metricsService.recordJobCompleted("TestJob", "DEFAULT", "SUCCESS");
        
        Counter counter = meterRegistry.find("jobs.completed.total")
            .tag("job_type", "TestJob")
            .tag("queue", "DEFAULT")
            .tag("status", "SUCCESS")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordExecutionTime() {
        Duration duration = Duration.ofSeconds(5);
        metricsService.recordExecutionTime("TestJob", "SUCCESS", duration);
        
        Timer timer = meterRegistry.find("jobs.execution.time")
            .tag("job_type", "TestJob")
            .tag("status", "SUCCESS")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS) >= 5);
    }

    @Test
    void testRecordQueueWaitTime() {
        Duration waitTime = Duration.ofSeconds(10);
        metricsService.recordQueueWaitTime("TestJob", "DEFAULT", waitTime);
        
        Timer timer = meterRegistry.find("jobs.queue.wait.time")
            .tag("job_type", "TestJob")
            .tag("queue", "DEFAULT")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void testRecordDbPollTime() {
        Duration pollTime = Duration.ofMillis(100);
        metricsService.recordDbPollTime("DEFAULT", pollTime);
        
        Timer timer = meterRegistry.find("jobs.db.poll.time")
            .tag("queue", "DEFAULT")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void testRecordEnqueueTime() {
        Duration enqueueTime = Duration.ofMillis(50);
        metricsService.recordEnqueueTime("TestJob", enqueueTime);
        
        Timer timer = meterRegistry.find("jobs.enqueue.time")
            .tag("job_type", "TestJob")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void testRecordJobException() {
        metricsService.recordJobException("TestJob", "RuntimeException");
        
        Counter counter = meterRegistry.find("jobs.failures.exception")
            .tag("job_type", "TestJob")
            .tag("exception_class", "RuntimeException")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordJobRetry() {
        metricsService.recordJobRetry("TestJob");
        
        Counter counter = meterRegistry.find("jobs.retries.total")
            .tag("job_type", "TestJob")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordDeadLetter() {
        metricsService.recordDeadLetter("TestJob");
        
        Counter counter = meterRegistry.find("jobs.deadletter.total")
            .tag("job_type", "TestJob")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordPollerLoop() {
        metricsService.recordPollerLoop("found_jobs");
        
        Counter counter = meterRegistry.find("jobs.poller.loops")
            .tag("outcome", "found_jobs")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordZombieJobReaped() {
        metricsService.recordZombieJobReaped("DEFAULT");
        
        Counter counter = meterRegistry.find("jobs.reaper.zombies")
            .tag("queue", "DEFAULT")
            .counter();
        
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void testRecordReaperExecutionTime() {
        Duration duration = Duration.ofMillis(200);
        metricsService.recordReaperExecutionTime(duration);
        
        Timer timer = meterRegistry.find("jobs.reaper.execution.time")
            .timer();
        
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void testRegisterWorkerActiveGauge() {
        Semaphore semaphore = new Semaphore(10);
        metricsService.registerWorkerActiveGauge("DEFAULT", semaphore, 10);
        
        var gauge = meterRegistry.find("jobs.worker.active")
            .tag("queue", "DEFAULT")
            .gauge();
        
        assertNotNull(gauge);
        assertEquals(0.0, gauge.value()); // All permits available, none active
        
        semaphore.tryAcquire(5);
        assertEquals(5.0, gauge.value()); // 5 permits taken = 5 active
    }

    @Test
    void testRegisterWorkerPermitsAvailableGauge() {
        Semaphore semaphore = new Semaphore(10);
        metricsService.registerWorkerPermitsAvailableGauge("DEFAULT", semaphore);
        
        var gauge = meterRegistry.find("jobs.worker.permits.available")
            .tag("queue", "DEFAULT")
            .gauge();
        
        assertNotNull(gauge);
        assertEquals(10.0, gauge.value());
        
        semaphore.tryAcquire(3);
        assertEquals(7.0, gauge.value());
    }

    @Test
    void testMultipleCallsIncrementCounters() {
        metricsService.recordJobEnqueued("TestJob", "DEFAULT");
        metricsService.recordJobEnqueued("TestJob", "DEFAULT");
        metricsService.recordJobEnqueued("TestJob", "DEFAULT");
        
        Counter counter = meterRegistry.find("jobs.enqueued.total")
            .tag("job_type", "TestJob")
            .tag("queue", "DEFAULT")
            .counter();
        
        assertNotNull(counter);
        assertEquals(3.0, counter.count());
    }

    @Test
    void testDifferentTagsCreateSeparateMetrics() {
        metricsService.recordJobCompleted("Job1", "DEFAULT", "SUCCESS");
        metricsService.recordJobCompleted("Job2", "DEFAULT", "SUCCESS");
        
        Counter counter1 = meterRegistry.find("jobs.completed.total")
            .tag("job_type", "Job1")
            .counter();
        
        Counter counter2 = meterRegistry.find("jobs.completed.total")
            .tag("job_type", "Job2")
            .counter();
        
        assertNotNull(counter1);
        assertNotNull(counter2);
        assertEquals(1.0, counter1.count());
        assertEquals(1.0, counter2.count());
    }
}
