# Kafka Bridge Usage Guide

This document describes how to use the Transactional Kafka Bridge implemented in ADR-004.

## Overview

The Kafka Bridge provides a bi-directional, transactional bridge between Kafka and the Job Platform, eliminating "dual write" bugs and providing a strangler fig migration path.

## Outbound Bridge (Publishing to Kafka)

### Basic Usage

Instead of publishing to Kafka directly, enqueue a `KafkaRelayJob`:

```java
import io.github.thestacktracewhisperer.jobs.shared.kafka.KafkaRelayJob;
import io.github.thestacktracewhisperer.jobs.producer.service.JobEnqueuer;

@Service
public class OrderService {
    private final JobEnqueuer jobEnqueuer;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void createOrder(Order order) {
        // 1. Persist order to database
        orderRepository.save(order);
        
        // 2. Enqueue Kafka relay job (within the same transaction)
        String payload = objectMapper.writeValueAsString(order);
        KafkaRelayJob job = new KafkaRelayJob(
            "orders.created.v1",     // topic
            order.getId().toString(), // key
            payload,                  // JSON payload
            Map.of("source", "order-service") // optional headers
        );
        jobEnqueuer.enqueue(job);
        
        // If this transaction rolls back, the Kafka message is never sent
    }
}
```

### Benefits

- **Transactional Safety**: If the database transaction rolls back, the Kafka message is never sent
- **Guaranteed Delivery**: The handler retries with exponential backoff if the broker is unavailable
- **Decoupled from Kafka**: Your business logic doesn't depend on Kafka availability

### Configuration

The `KafkaRelayHandler` uses these defaults:
- Queue: `KAFKA_RELAY`
- Max payload size: 1MB (configurable)
- Send timeout: 10 seconds
- Retries: Handled by job platform (exponential backoff)

To configure ordering:
```yaml
platform:
  jobs:
    worker:
      queues:
        KAFKA_RELAY:
          concurrency: 1  # Strict ordering (see ADR-004 Risk Assessment)
```

## Inbound Bridge (Consuming from Kafka)

### Basic Usage

Create an ingestor that converts Kafka messages to domain jobs:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class MyKafkaIngestor {
    private final JobEnqueuer jobEnqueuer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "my.topic.v1",
        groupId = "my-consumer-group",
        containerFactory = "kafkaManualAckListenerContainerFactory"
    )
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        try {
            // 1. Deserialize
            MyEvent event = objectMapper.readValue(record.value(), MyEvent.class);

            // 2. Transform to domain job
            MyDomainJob job = new MyDomainJob(event.getId(), event.getData());

            // 3. Persist to job table
            jobEnqueuer.enqueue(job);

            // 4. Commit Kafka offset
            ack.acknowledge();

        } catch (JsonProcessingException e) {
            // Poison pill - acknowledge to unblock partition
            log.error("POISON PILL: Cannot deserialize message at offset {}", 
                record.offset(), e);
            ack.acknowledge();
            
        } catch (Exception e) {
            // Transient failure - let Kafka retry
            log.error("Transient failure at offset {}", record.offset(), e);
            throw e;
            
        } finally {
            MDC.clear();
        }
    }
}
```

### Benefits

- **Transactional Safety**: Kafka offset commits only after successful DB insert
- **Poison Pill Strategy**: Bad messages don't block the partition
- **Exponential Backoff**: Prevents retry storms during outages
- **Simple Logic**: Business logic moves to the JobHandler

### Configuration

The `KafkaBridgeConfig` provides:
- Manual acknowledgment mode
- Exponential backoff: 1s → 2s → 4s → ... → 60s (max 5 minutes)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: my-consumer-group
      enable-auto-commit: false
```

## Risk Mitigations

### Ordering

**Risk**: Parallel job processing can break Kafka's per-partition ordering guarantee.

**Mitigation**: Set `KAFKA_RELAY` queue to `concurrency: 1` if strict ordering is required.

### Message Size

**Risk**: Large payloads (>1MB) can cause infinite retries.

**Mitigation**: The handler validates payload size before sending. Oversized messages fail permanently.

### Duplicate Delivery

**Risk**: Network failure after DB commit but before Kafka ACK causes redelivery.

**Mitigation**: Make your JobHandlers idempotent:

```java
@Override
public void handle(MyDomainJob job) {
    // Check if already processed
    if (orderService.isAlreadyFulfilled(job.orderId())) {
        log.info("Order {} already fulfilled, skipping", job.orderId());
        return;
    }
    
    // Process the order
    orderService.fulfill(job.orderId());
}
```

## Testing

See the test classes for examples:
- `KafkaRelayHandlerTest`: Unit tests for the relay handler
- `LegacyOrderIngestorTest`: Unit tests for the ingestor

## Migration Strategy

1. **Start Small**: Migrate one topic at a time
2. **Add Relay**: Replace direct Kafka publishing with `KafkaRelayJob`
3. **Add Ingestor**: Replace complex Kafka consumers with simple ingestors
4. **Monitor**: Watch for poison pills, retry storms, and duplicates
5. **Iterate**: Adjust concurrency and backoff settings as needed
