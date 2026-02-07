package io.github.thestacktracewhisperer.jobs.ui.config;

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
@EnableJpaRepositories(basePackages = "io.github.thestacktracewhisperer.jobs.common.entity")
@EntityScan(basePackages = "io.github.thestacktracewhisperer.jobs.common.entity")
public class JobPlatformUiAutoConfiguration {
}
