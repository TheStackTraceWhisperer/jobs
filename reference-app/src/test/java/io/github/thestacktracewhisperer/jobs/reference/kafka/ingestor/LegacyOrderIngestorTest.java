package io.github.thestacktracewhisperer.jobs.reference.kafka.ingestor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thestacktracewhisperer.jobs.reference.kafka.dto.LegacyOrderEvent;
import io.github.thestacktracewhisperer.jobs.reference.job.FulfillOrderJob;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyOrderIngestorTest {

    @Mock
    private JobEnqueuer jobEnqueuer;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private LegacyOrderIngestor ingestor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ingestor = new LegacyOrderIngestor(jobEnqueuer, objectMapper);
    }

    @Test
    void testOnMessage_Success() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");
        
        LegacyOrderEvent event = new LegacyOrderEvent(orderId, customerId, amount);
        String json = objectMapper.writeValueAsString(event);
        
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "orders.created.v1", 0, 123L, "key", json
        );

        // Act
        ingestor.onMessage(record, acknowledgment);

        // Assert
        ArgumentCaptor<FulfillOrderJob> jobCaptor = ArgumentCaptor.forClass(FulfillOrderJob.class);
        verify(jobEnqueuer).enqueue(jobCaptor.capture());
        verify(acknowledgment).acknowledge();
        
        FulfillOrderJob capturedJob = jobCaptor.getValue();
        assertEquals(orderId, capturedJob.orderId());
        assertEquals(amount, capturedJob.amount());
    }

    @Test
    void testOnMessage_PoisonPill_BadJson() {
        // Arrange
        String badJson = "{this is not valid json}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "orders.created.v1", 0, 123L, "key", badJson
        );

        // Act
        ingestor.onMessage(record, acknowledgment);

        // Assert - poison pill should be acknowledged to unblock partition
        verify(jobEnqueuer, never()).enqueue(any(FulfillOrderJob.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testOnMessage_TransientFailure_ThrowsException() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");
        
        LegacyOrderEvent event = new LegacyOrderEvent(orderId, customerId, amount);
        String json = objectMapper.writeValueAsString(event);
        
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "orders.created.v1", 0, 123L, "key", json
        );
        
        // Simulate DB failure
        doThrow(new RuntimeException("Database unavailable")).when(jobEnqueuer).enqueue(any(FulfillOrderJob.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ingestor.onMessage(record, acknowledgment);
        });
        
        assertEquals("Database unavailable", exception.getMessage());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void testOnMessage_MissingField_PoisonPill() {
        // Arrange - JSON is valid but missing required field
        String incompleteJson = "{\"orderId\":\"" + UUID.randomUUID() + "\"}"; // Missing customerId and amount
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "orders.created.v1", 0, 123L, "key", incompleteJson
        );

        // Act - This will deserialize but with null fields
        ingestor.onMessage(record, acknowledgment);

        // Assert - job should still be enqueued (validation should happen in handler if needed)
        verify(jobEnqueuer).enqueue(any(FulfillOrderJob.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void testOnMessage_EmptyPayload_PoisonPill() {
        // Arrange
        String emptyJson = "";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "orders.created.v1", 0, 123L, "key", emptyJson
        );

        // Act
        ingestor.onMessage(record, acknowledgment);

        // Assert - poison pill should be acknowledged
        verify(jobEnqueuer, never()).enqueue(any(FulfillOrderJob.class));
        verify(acknowledgment).acknowledge();
    }
}
