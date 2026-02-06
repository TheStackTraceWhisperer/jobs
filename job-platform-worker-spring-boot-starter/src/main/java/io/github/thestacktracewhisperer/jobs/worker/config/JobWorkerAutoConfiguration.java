package io.github.thestacktracewhisperer.jobs.worker.config;

import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for job worker functionality.
 */
@AutoConfiguration
@EnableConfigurationProperties(JobWorkerProperties.class)
@ConditionalOnProperty(prefix = "platform.jobs.worker", name = "enabled", havingValue = "true")
@EnableScheduling
@ComponentScan(basePackages = "io.github.thestacktracewhisperer.jobs.worker")
@Import(SpringCloudRefreshScopeConfiguration.class)
public class JobWorkerAutoConfiguration {
}
