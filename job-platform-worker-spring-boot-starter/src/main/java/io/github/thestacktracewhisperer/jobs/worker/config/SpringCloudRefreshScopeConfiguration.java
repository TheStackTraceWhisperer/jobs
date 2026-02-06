package io.github.thestacktracewhisperer.jobs.worker.config;

import io.github.thestacktracewhisperer.jobs.worker.properties.JobWorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration for Spring Cloud refresh scope support.
 * This configuration is only active when Spring Cloud Context is on the classpath.
 * It wraps JobWorkerProperties with @RefreshScope to enable dynamic configuration refresh.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
@ConditionalOnProperty(prefix = "platform.jobs.worker", name = "enabled", havingValue = "true")
public class SpringCloudRefreshScopeConfiguration {

    /**
     * Creates a JobWorkerProperties bean with @RefreshScope enabled.
     * This allows the properties to be refreshed dynamically without restarting the application.
     * This bean is marked as @Primary to override the default JobWorkerProperties bean created
     * by @EnableConfigurationProperties when Spring Cloud is available.
     * 
     * @return JobWorkerProperties bean with refresh scope
     */
    @Bean
    @RefreshScope
    @Primary
    public JobWorkerProperties jobWorkerPropertiesRefreshScope() {
        return new JobWorkerProperties();
    }
}
