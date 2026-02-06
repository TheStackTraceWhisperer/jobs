package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Maintenance service that reaps zombie jobs (jobs stuck in PROCESSING status).
 * Zombie jobs are reset to QUEUED status with incremented attempts.
 */
@Component
@ConditionalOnProperty(prefix = "platform.jobs.worker", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MaintenanceService {

    private final JobRepository jobRepository;
    private final JobWorkerProperties properties;
    private final JobMetricsService metricsService;
    private final MeterRegistry meterRegistry;
    
    // Cache for queue metrics to avoid recreating gauges
    private final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> queueDepthCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> queueAgeCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Reaps zombie jobs that haven't updated their heartbeat.
     * Runs on a fixed delay schedule (default: every minute).
     */
    @Scheduled(fixedDelayString = "${platform.jobs.worker.reaper-interval-ms:60000}")
    @Transactional
    public void reapZombieJobs() {
        Instant reaperStart = Instant.now();
        
        try {
            Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.getZombieThresholdMinutes()));

            List<JobEntity> zombies = jobRepository.findZombieJobs(
                JobStatus.PROCESSING, threshold);

            if (zombies.isEmpty()) {
                return;
            }

            log.warn("Found {} zombie jobs, resetting to QUEUED", zombies.size());

            for (JobEntity zombie : zombies) {
                zombie.setStatus(JobStatus.QUEUED);
                zombie.setLastError("Job timed out - no heartbeat update");
                zombie.setRunAt(Instant.now());
                jobRepository.save(zombie);
                
                // Record zombie reaping metric
                metricsService.recordZombieJobReaped(zombie.getQueueName());

                log.info("Reset zombie job to QUEUED: id={}, lastHeartbeat={}", 
                    zombie.getId(), zombie.getLastHeartbeat());
            }

        } catch (Exception e) {
            log.error("Error during zombie job reaping", e);
        } finally {
            Duration reaperDuration = Duration.between(reaperStart, Instant.now());
            metricsService.recordReaperExecutionTime(reaperDuration);
        }
    }

    /**
     * Updates queue depth and age metrics.
     * Runs on a fixed delay schedule (default: every 15 seconds).
     * This is separated from polling to avoid performance impact.
     */
    @Scheduled(fixedDelayString = "${platform.jobs.worker.queue-metrics-interval-ms:15000}")
    @Transactional(readOnly = true)
    public void updateQueueMetrics() {
        try {
            List<Object[]> statsResults = jobRepository.getQueueStatisticsNative();
            
            for (Object[] row : statsResults) {
                String queueName = (String) row[0];
                long queuedCount = ((Number) row[1]).longValue();
                Long oldestJobAgeSeconds = row[2] != null ? ((Number) row[2]).longValue() : null;
                
                Tags tags = Tags.of("queue", queueName);
                
                // Update or create queue depth gauge
                java.util.concurrent.atomic.AtomicLong depthHolder = queueDepthCache.computeIfAbsent(queueName, 
                    k -> {
                        java.util.concurrent.atomic.AtomicLong holder = new java.util.concurrent.atomic.AtomicLong(0);
                        meterRegistry.gauge("jobs.queue.depth", tags, holder, java.util.concurrent.atomic.AtomicLong::get);
                        return holder;
                    });
                depthHolder.set(queuedCount);
                
                // Update or create oldest job age gauge
                if (oldestJobAgeSeconds != null) {
                    java.util.concurrent.atomic.AtomicLong ageHolder = queueAgeCache.computeIfAbsent(queueName,
                        k -> {
                            java.util.concurrent.atomic.AtomicLong holder = new java.util.concurrent.atomic.AtomicLong(0);
                            meterRegistry.gauge("jobs.queue.oldest.age", tags, holder, java.util.concurrent.atomic.AtomicLong::get);
                            return holder;
                        });
                    ageHolder.set(oldestJobAgeSeconds);
                }
            }
            
        } catch (Exception e) {
            log.error("Error updating queue metrics", e);
        }
    }
}
