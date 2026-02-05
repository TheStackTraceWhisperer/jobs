package io.github.thestacktracewhisperer.jobs.reference.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that provides an ObjectMapper configured to ignore unknown properties.
 * This handles the case where Job interface methods like getCompensatingJob() are serialized
 * but the record doesn't have corresponding fields.
 */
@TestConfiguration
public class TestObjectMapperConfig {
    
    @Bean
    @Primary
    public ObjectMapper testObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
