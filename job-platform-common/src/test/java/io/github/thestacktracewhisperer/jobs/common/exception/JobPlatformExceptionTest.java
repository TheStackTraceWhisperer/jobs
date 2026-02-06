package io.github.thestacktracewhisperer.jobs.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobPlatformExceptionTest {

    @Test
    void testConstructorWithMessage() {
        String message = "Test error message";
        JobPlatformException exception = new JobPlatformException(message);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testConstructorWithMessageAndCause() {
        String message = "Test error message";
        Throwable cause = new RuntimeException("Root cause");
        
        JobPlatformException exception = new JobPlatformException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testIsRuntimeException() {
        JobPlatformException exception = new JobPlatformException("Test");
        
        assertTrue(exception instanceof RuntimeException);
    }
}
