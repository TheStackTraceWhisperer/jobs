package io.github.thestacktracewhisperer.jobs.kafka.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KafkaBrokerExceptionTest {

    @Test
    void testExceptionWithMessageAndCause() {
        // Arrange
        String message = "Kafka broker unavailable";
        Exception cause = new RuntimeException("Connection refused");
        
        // Act
        KafkaBrokerException exception = new KafkaBrokerException(message, cause);
        
        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
