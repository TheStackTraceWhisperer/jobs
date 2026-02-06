package io.github.thestacktracewhisperer.jobs.common.exception;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JobSnoozeExceptionTest {

    @Test
    void testConstructor() {
        String message = "Resource not ready";
        Duration delay = Duration.ofMinutes(5);
        
        JobSnoozeException exception = new JobSnoozeException(message, delay);
        
        assertEquals(message, exception.getMessage());
        assertEquals(delay, exception.getDelay());
    }

    @Test
    void testExtendsRuntimeException() {
        JobSnoozeException exception = new JobSnoozeException("Test", Duration.ofSeconds(10));
        
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void testWithZeroDuration() {
        Duration delay = Duration.ZERO;
        JobSnoozeException exception = new JobSnoozeException("Test", delay);
        
        assertEquals(delay, exception.getDelay());
        assertEquals(0, exception.getDelay().getSeconds());
    }

    @Test
    void testWithLongDuration() {
        Duration delay = Duration.ofHours(2);
        JobSnoozeException exception = new JobSnoozeException("Long wait", delay);
        
        assertEquals(delay, exception.getDelay());
        assertEquals(7200, exception.getDelay().getSeconds());
    }

    @Test
    void testGetDelay() {
        Duration delay = Duration.ofMinutes(15);
        JobSnoozeException exception = new JobSnoozeException("Test", delay);
        
        Duration retrievedDelay = exception.getDelay();
        
        assertNotNull(retrievedDelay);
        assertEquals(delay, retrievedDelay);
    }
}
