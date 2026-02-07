package io.github.thestacktracewhisperer.jobs.ui.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for Job Platform UI.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "platform.jobs.ui.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "io.github.thestacktracewhisperer.jobs.ui")
public class JobPlatformUiAutoConfiguration {
}
