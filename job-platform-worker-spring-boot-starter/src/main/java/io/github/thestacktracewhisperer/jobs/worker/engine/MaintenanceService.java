package io.github.thestacktracewhisperer.jobs.worker.engine;

import io.github.thestacktracewhisperer.jobs.common.entity.JobEntity;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.entity.JobStatus;
import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
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

    /**
     * Reaps zombie jobs that haven't updated their heartbeat.
     * Runs on a fixed delay schedule (default: every minute).
     */
    @Scheduled(fixedDelayString = "${platform.jobs.worker.reaper-interval-ms:60000}")
    @Transactional
    public void reapZombieJobs() {
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

                log.info("Reset zombie job to QUEUED: id={}, lastHeartbeat={}", 
                    zombie.getId(), zombie.getLastHeartbeat());
            }

        } catch (Exception e) {
            log.error("Error during zombie job reaping", e);
        }
    }
}
