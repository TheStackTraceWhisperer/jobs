package io.github.thestacktracewhisperer.jobs.worker.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.exception.JobHandlerNotFoundException;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSerializationException;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Routes jobs to their appropriate handlers based on job type.
 * Uses ObjectProvider to discover all JobHandler beans on startup.
 */
@Component
@RequiredArgsConstructor
public class JobRoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(JobRoutingEngine.class);

    private final ObjectProvider<JobHandler<?>> handlersProvider;
    private final ObjectMapper objectMapper;
    private final Map<String, JobHandler<?>> handlerRegistry = new HashMap<>();

    /**
     * Uses ObjectProvider to discover all JobHandler beans and builds a registry.
     */
    @PostConstruct
    public void initialize() {
        handlersProvider.stream().forEach(handler -> {
            try {
                // Use reflection to determine the job type
                Class<?> jobType = getJobTypeForHandler(handler);
                String jobTypeName = jobType.getName();
                handlerRegistry.put(jobTypeName, handler);
                log.info("Registered handler for job type: {}", jobTypeName);
            } catch (Exception e) {
                log.warn("Failed to register handler: {}", handler.getClass().getName(), e);
            }
        });
        
        log.info("Job routing engine initialized with {} handlers", handlerRegistry.size());
    }

    /**
     * Routes a job to its handler and executes it.
     * 
     * @param jobType the fully qualified class name of the job
     * @param payload the JSON payload of the job
     * @throws Exception if job execution fails
     */
    @SuppressWarnings("unchecked")
    public void route(String jobType, String payload) throws Exception {
        JobHandler handler = handlerRegistry.get(jobType);
        if (handler == null) {
            throw new JobHandlerNotFoundException(jobType);
        }

        try {
            // Deserialize the job
            Class<?> jobClass = Class.forName(jobType);
            Job job = (Job) objectMapper.readValue(payload, jobClass);

            // Execute the handler
            handler.handle(job);
            
        } catch (ClassNotFoundException e) {
            throw new JobSerializationException("Job class not found: " + jobType, e);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JobSerializationException("Failed to deserialize job: " + jobType, e);
        }
    }

    /**
     * Gets the job handler for a specific job type.
     * 
     * @param jobType the fully qualified class name of the job
     * @return the job handler, or null if not found
     */
    public JobHandler<?> getHandler(String jobType) {
        return handlerRegistry.get(jobType);
    }
    
    /**
     * Returns the set of all job type class names that have registered handlers.
     * This allows workers to filter database queries to only fetch jobs they can process.
     * 
     * @return immutable set of job type class names
     */
    public java.util.Set<String> getRegisteredJobTypes() {
        return java.util.Collections.unmodifiableSet(handlerRegistry.keySet());
    }

    /**
     * Extracts the job type from a handler using reflection.
     */
    private Class<?> getJobTypeForHandler(JobHandler<?> handler) {
        try {
            java.lang.reflect.Type[] genericInterfaces = handler.getClass().getGenericInterfaces();
            for (java.lang.reflect.Type genericInterface : genericInterfaces) {
                if (genericInterface instanceof java.lang.reflect.ParameterizedType) {
                    java.lang.reflect.ParameterizedType paramType = 
                        (java.lang.reflect.ParameterizedType) genericInterface;
                    if (JobHandler.class.isAssignableFrom((Class<?>) paramType.getRawType())) {
                        return (Class<?>) paramType.getActualTypeArguments()[0];
                    }
                }
            }
            throw new IllegalStateException("Could not determine job type for handler: " 
                + handler.getClass().getName());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract job type from handler", e);
        }
    }
}
