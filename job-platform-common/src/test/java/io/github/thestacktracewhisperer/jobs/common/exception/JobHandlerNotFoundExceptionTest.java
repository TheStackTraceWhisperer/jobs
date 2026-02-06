package io.github.thestacktracewhisperer.jobs.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobHandlerNotFoundExceptionTest {

    @Test
    void testConstructor() {
        String jobType = "com.example.MyJob";
        JobHandlerNotFoundException exception = new JobHandlerNotFoundException(jobType);
        
        assertEquals("No handler found for job type: " + jobType, exception.getMessage());
    }

    @Test
    void testExtendsJobPlatformException() {
        JobHandlerNotFoundException exception = new JobHandlerNotFoundException("test");
        
        assertTrue(exception instanceof JobPlatformException);
    }

    @Test
    void testWithNullJobType() {
        JobHandlerNotFoundException exception = new JobHandlerNotFoundException(null);
        
        assertEquals("No handler found for job type: null", exception.getMessage());
    }

    @Test
    void testWithEmptyJobType() {
        JobHandlerNotFoundException exception = new JobHandlerNotFoundException("");
        
        assertEquals("No handler found for job type: ", exception.getMessage());
    }
}
