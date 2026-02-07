package io.github.thestacktracewhisperer.jobs.ui.config;

import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for Job Platform UI.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "platform.jobs.ui.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "io.github.thestacktracewhisperer.jobs.ui")
@EnableJpaRepositories(basePackageClasses = JobRepository.class)
@EntityScan(basePackages = "io.github.thestacktracewhisperer.jobs.common.entity")
public class JobPlatformUiAutoConfiguration {
}
