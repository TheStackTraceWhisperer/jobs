package io.github.thestacktracewhisperer.jobs.worker.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.common.exception.JobHandlerNotFoundException;
import io.github.thestacktracewhisperer.jobs.common.exception.JobSerializationException;
import io.github.thestacktracewhisperer.jobs.common.model.Job;
import io.github.thestacktracewhisperer.jobs.worker.handler.JobHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobRoutingEngineTest {

    @Mock
    private ObjectProvider<JobHandler<?>> handlersProvider;

    @Mock
    private ObjectMapper objectMapper;

    private JobRoutingEngine routingEngine;

    private static class TestJob implements Job {
        public String data;
    }

    private static class AnotherJob implements Job {
        public int value;
    }

    private static class TestJobHandler implements JobHandler<TestJob> {
        boolean handled = false;

        @Override
        public void handle(TestJob job) {
            handled = true;
        }
    }

    private static class AnotherJobHandler implements JobHandler<AnotherJob> {
        boolean handled = false;

        @Override
        public void handle(AnotherJob job) {
            handled = true;
        }
    }

    @BeforeEach
    void setUp() {
        routingEngine = new JobRoutingEngine(handlersProvider, objectMapper);
    }

    @Test
    void testInitializeRegistersHandlers() {
        TestJobHandler testHandler = new TestJobHandler();
        AnotherJobHandler anotherHandler = new AnotherJobHandler();

        when(handlersProvider.stream()).thenReturn(Stream.of(testHandler, anotherHandler));

        routingEngine.initialize();

        assertEquals(2, routingEngine.getRegisteredJobTypes().size());
        assertTrue(routingEngine.getRegisteredJobTypes().contains(TestJob.class.getName()));
        assertTrue(routingEngine.getRegisteredJobTypes().contains(AnotherJob.class.getName()));
    }

    @Test
    void testInitializeWithNoHandlers() {
        when(handlersProvider.stream()).thenReturn(Stream.empty());

        routingEngine.initialize();

        assertTrue(routingEngine.getRegisteredJobTypes().isEmpty());
    }

    @Test
    void testRouteSuccessfully() throws Exception {
        TestJobHandler handler = new TestJobHandler();
        when(handlersProvider.stream()).thenReturn(Stream.of(handler));
        routingEngine.initialize();

        String jobType = TestJob.class.getName();
        String payload = "{\"data\":\"test\"}";
        TestJob job = new TestJob();
        job.data = "test";

        when(objectMapper.readValue(payload, TestJob.class)).thenReturn(job);

        routingEngine.route(jobType, payload);

        assertTrue(handler.handled);
        verify(objectMapper).readValue(payload, TestJob.class);
    }

    @Test
    void testRouteThrowsJobHandlerNotFoundException() {
        when(handlersProvider.stream()).thenReturn(Stream.empty());
        routingEngine.initialize();

        String jobType = "UnknownJob";
        String payload = "{}";

        assertThrows(JobHandlerNotFoundException.class, () -> {
            routingEngine.route(jobType, payload);
        });
    }

    @Test
    void testRouteThrowsSerializationExceptionForInvalidJson() throws Exception {
        TestJobHandler handler = new TestJobHandler();
        when(handlersProvider.stream()).thenReturn(Stream.of(handler));
        routingEngine.initialize();

        String jobType = TestJob.class.getName();
        String payload = "invalid json";

        when(objectMapper.readValue(eq(payload), any(Class.class)))
            .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Invalid JSON"));

        assertThrows(JobSerializationException.class, () -> {
            routingEngine.route(jobType, payload);
        });
    }

    @Test
    void testRouteThrowsHandlerNotFoundExceptionForClassNotFound() {
        when(handlersProvider.stream()).thenReturn(Stream.empty());
        routingEngine.initialize();

        String jobType = "com.nonexistent.Job";
        String payload = "{}";

        assertThrows(JobHandlerNotFoundException.class, () -> {
            routingEngine.route(jobType, payload);
        });
    }

    @Test
    void testGetHandler() {
        TestJobHandler handler = new TestJobHandler();
        when(handlersProvider.stream()).thenReturn(Stream.of(handler));
        routingEngine.initialize();

        JobHandler<?> retrievedHandler = routingEngine.getHandler(TestJob.class.getName());

        assertNotNull(retrievedHandler);
        assertSame(handler, retrievedHandler);
    }

    @Test
    void testGetHandlerReturnsNullForUnknownType() {
        when(handlersProvider.stream()).thenReturn(Stream.empty());
        routingEngine.initialize();

        JobHandler<?> handler = routingEngine.getHandler("UnknownJob");

        assertNull(handler);
    }

    @Test
    void testGetRegisteredJobTypesReturnsUnmodifiableSet() {
        TestJobHandler handler = new TestJobHandler();
        when(handlersProvider.stream()).thenReturn(Stream.of(handler));
        routingEngine.initialize();

        var jobTypes = routingEngine.getRegisteredJobTypes();

        assertThrows(UnsupportedOperationException.class, () -> {
            jobTypes.add("NewJob");
        });
    }

    @Test
    void testRouteExecutesHandlerWithCorrectJob() throws Exception {
        AnotherJobHandler handler = new AnotherJobHandler();
        when(handlersProvider.stream()).thenReturn(Stream.of(handler));
        routingEngine.initialize();

        String jobType = AnotherJob.class.getName();
        String payload = "{\"value\":42}";
        AnotherJob job = new AnotherJob();
        job.value = 42;

        when(objectMapper.readValue(payload, AnotherJob.class)).thenReturn(job);

        routingEngine.route(jobType, payload);

        assertTrue(handler.handled);
    }

    @Test
    void testInitializeHandlesHandlerRegistrationFailureGracefully() {
        JobHandler<?> invalidHandler = new JobHandler<Job>() {
            @Override
            public void handle(Job job) throws Exception {
            }
        };

        when(handlersProvider.stream()).thenReturn(Stream.of(invalidHandler));

        // Should not throw exception
        assertDoesNotThrow(() -> routingEngine.initialize());
    }

    @Test
    void testMultipleHandlersRegistered() {
        TestJobHandler testHandler = new TestJobHandler();
        AnotherJobHandler anotherHandler = new AnotherJobHandler();

        when(handlersProvider.stream()).thenReturn(Stream.of(testHandler, anotherHandler));
        routingEngine.initialize();

        assertNotNull(routingEngine.getHandler(TestJob.class.getName()));
        assertNotNull(routingEngine.getHandler(AnotherJob.class.getName()));
        
        assertSame(testHandler, routingEngine.getHandler(TestJob.class.getName()));
        assertSame(anotherHandler, routingEngine.getHandler(AnotherJob.class.getName()));
    }
}
