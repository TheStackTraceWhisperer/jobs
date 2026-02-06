package io.github.thestacktracewhisperer.jobs.producer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.metrics.JobMetricsService;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JobProducerAutoConfigurationTest {

    @Test
    void testJobObjectMapperCreation() {
        JobProducerAutoConfiguration config = new JobProducerAutoConfiguration();
        
        ObjectMapper objectMapper = config.jobObjectMapper();
        
        assertNotNull(objectMapper);
    }

    @Test
    void testJobMetricsServiceCreation() {
        JobProducerAutoConfiguration config = new JobProducerAutoConfiguration();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        JobMetricsService service = config.jobMetricsService(meterRegistry);
        
        assertNotNull(service);
    }

    @Test
    void testJobEnqueuerCreation() {
        JobProducerAutoConfiguration config = new JobProducerAutoConfiguration();
        
        JobEnqueuer enqueuer = config.jobEnqueuer(
            mock(io.github.thestacktracewhisperer.jobs.common.entity.JobRepository.class),
            new ObjectMapper(),
            new JobMetricsService(new SimpleMeterRegistry())
        );
        
        assertNotNull(enqueuer);
    }
}
