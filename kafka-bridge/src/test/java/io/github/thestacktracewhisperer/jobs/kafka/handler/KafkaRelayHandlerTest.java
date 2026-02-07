package io.github.thestacktracewhisperer.jobs.kafka.handler;

import io.github.thestacktracewhisperer.jobs.kafka.exception.KafkaBrokerException;
import io.github.thestacktracewhisperer.jobs.kafka.exception.PayloadTooLargeException;
import io.github.thestacktracewhisperer.jobs.kafka.properties.KafkaRelayProperties;
import io.github.thestacktracewhisperer.jobs.shared.kafka.KafkaRelayJob;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaRelayHandlerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private KafkaRelayProperties properties;

    private KafkaRelayHandler handler;

    @BeforeEach
    void setUp() {
        // Use lenient stubbing since not all tests use all stubbed methods
        lenient().when(properties.getMaxMessageSize()).thenReturn(1024 * 1024); // 1MB
        lenient().when(properties.getSendTimeoutSeconds()).thenReturn(10);
        handler = new KafkaRelayHandler(kafkaTemplate, properties);
    }

    @Test
    void testHandle_Success() throws Exception {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"orderId\":\"123\"}";
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, null);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        handler.handle(job);

        // Assert
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals(topic, record.topic());
        assertEquals(key, record.key());
        assertEquals(payload, record.value());
    }

    @Test
    void testHandle_WithHeaders() throws Exception {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"orderId\":\"123\"}";
        Map<String, String> headers = Map.of("correlation-id", "abc-123", "source", "test");
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, headers);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        handler.handle(job);

        // Assert
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals(2, record.headers().toArray().length);
        assertEquals("abc-123", new String(record.headers().lastHeader("correlation-id").value(), StandardCharsets.UTF_8));
        assertEquals("test", new String(record.headers().lastHeader("source").value(), StandardCharsets.UTF_8));
    }

    @Test
    void testHandle_BrokerTimeout() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"orderId\":\"123\"}";
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, null);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new TimeoutException("Broker timeout"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        KafkaBrokerException exception = assertThrows(KafkaBrokerException.class, () -> handler.handle(job));
        assertTrue(exception.getMessage().contains("Kafka Broker unreachable or timed out"));
    }

    @Test
    void testHandle_BrokerExecutionFailure() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"orderId\":\"123\"}";
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, null);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new ExecutionException("Broker error", new RuntimeException("Connection refused")));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        KafkaBrokerException exception = assertThrows(KafkaBrokerException.class, () -> handler.handle(job));
        assertTrue(exception.getMessage().contains("Kafka Broker unreachable or timed out"));
    }

    @Test
    void testHandle_PayloadTooLarge() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        // Create a payload larger than 1MB (the default max)
        StringBuilder largePayload = new StringBuilder();
        for (int i = 0; i < 1100000; i++) {
            largePayload.append("x");
        }
        KafkaRelayJob job = new KafkaRelayJob(topic, key, largePayload.toString(), null);

        // Act & Assert
        PayloadTooLargeException exception = assertThrows(PayloadTooLargeException.class, () -> handler.handle(job));
        assertTrue(exception.getMessage().contains("Payload size"));
        assertTrue(exception.getMessage().contains("exceeds Kafka max message size"));
        
        // Verify kafkaTemplate.send was never called
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void testHandle_InterruptedException() {
        // Arrange
        String topic = "test-topic";
        String key = "test-key";
        String payload = "{\"orderId\":\"123\"}";
        KafkaRelayJob job = new KafkaRelayJob(topic, key, payload, null);

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        // ExecutionException wraps the InterruptedException
        future.completeExceptionally(new ExecutionException("Thread interrupted", new InterruptedException("Thread interrupted")));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        // The handler catches ExecutionException and throws KafkaBrokerException
        KafkaBrokerException exception = assertThrows(KafkaBrokerException.class, () -> handler.handle(job));
        assertTrue(exception.getMessage().contains("Kafka Broker unreachable or timed out"));
    }
}
