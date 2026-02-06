package io.github.thestacktracewhisperer.jobs.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobSerializationExceptionTest {

    @Test
    void testConstructor() {
        String message = "Serialization failed";
        Throwable cause = new RuntimeException("JSON error");
        
        JobSerializationException exception = new JobSerializationException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testExtendsJobPlatformException() {
        JobSerializationException exception = new JobSerializationException(
            "Test", new RuntimeException());
        
        assertTrue(exception instanceof JobPlatformException);
    }

    @Test
    void testWithNullCause() {
        String message = "Test message";
        JobSerializationException exception = new JobSerializationException(message, null);
        
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }
}
