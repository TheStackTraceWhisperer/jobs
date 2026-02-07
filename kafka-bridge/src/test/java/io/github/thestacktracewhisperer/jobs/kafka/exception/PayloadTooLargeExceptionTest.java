package io.github.thestacktracewhisperer.jobs.kafka.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadTooLargeExceptionTest {

    @Test
    void testExceptionWithMessage() {
        // Arrange
        String message = "Payload size exceeds maximum";
        
        // Act
        PayloadTooLargeException exception = new PayloadTooLargeException(message);
        
        // Assert
        assertEquals(message, exception.getMessage());
    }
}
