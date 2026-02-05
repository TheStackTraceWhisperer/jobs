package io.github.thestacktracewhisperer.jobs.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.thestacktracewhisperer.jobs.common.entity.JobRepository;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
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
    public ObjectMapper jobObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public JobEnqueuer jobEnqueuer(JobRepository jobRepository, ObjectMapper objectMapper) {
        return new JobEnqueuer(jobRepository, objectMapper);
    }
}
