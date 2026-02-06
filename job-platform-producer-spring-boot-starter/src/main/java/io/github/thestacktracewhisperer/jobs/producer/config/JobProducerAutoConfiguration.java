package io.github.thestacktracewhisperer.jobs.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.thestacktracewhisperer.jobs.common.annotation.PlatformJsonSerializer;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Auto-configuration for job producer functionality.
 */
@AutoConfiguration
@EnableJpaRepositories(basePackageClasses = JobRepository.class)
@EntityScan(basePackages = "io.github.thestacktracewhisperer.jobs.common.entity")
public class JobProducerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @PlatformJsonSerializer
    public ObjectMapper jobObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public JobMetricsService jobMetricsService(MeterRegistry meterRegistry) {
        return new JobMetricsService(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobEnqueuer jobEnqueuer(JobRepository jobRepository, 
                                   @PlatformJsonSerializer ObjectMapper objectMapper,
                                   JobMetricsService metricsService) {
        return new JobEnqueuer(jobRepository, objectMapper, metricsService);
    }
}
