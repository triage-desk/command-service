package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.outbox;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.OutboxEventStatus;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.OutboxProperties;
import dev.eyad_sharkawy.triage_desk.command_service.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.NullMarked;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final String SERVICE_NAME = "triage-command-service";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    @Scheduled(fixedDelayString = "${app.outbox.polling-delay-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents =
                outboxEventRepository.findPendingEventsForProcessing(outboxProperties.batchSize());

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox event(s)", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEventEntity event) {
        String partitionKey = event.getAggregateId().toString();

        ProducerRecord<String, String> producerRecord =
                new ProducerRecord<>(outboxProperties.topic(), partitionKey, event.getPayload());

        producerRecord
                .headers()
                .add("X-Source-Service", SERVICE_NAME.getBytes(StandardCharsets.UTF_8));
        producerRecord
                .headers()
                .add(
                        "X-Tenant-ID",
                        event.getTenantId().toString().getBytes(StandardCharsets.UTF_8));
        producerRecord
                .headers()
                .add("X-Event-Type", event.getEventType().getBytes(StandardCharsets.UTF_8));

        try {
            kafkaTemplate.send(producerRecord).get(5, TimeUnit.SECONDS);

            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setProcessedAt(Instant.now());
            event.setErrorMessage(null);
            log.debug("Outbox event {} published successfully to Kafka", event.getId());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            handleEventExceptions(event, ex);
        } catch (ExecutionException | TimeoutException ex) {
            handleEventExceptions(event, ex);
        }
    }

    private void handleEventExceptions(OutboxEventEntity event, Exception ex) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);
        event.setErrorMessage(ex.getMessage());
        if (newRetryCount >= outboxProperties.maxRetries()) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error(
                    "Outbox event {} marked FAILED after {} retries: {}",
                    event.getId(),
                    newRetryCount,
                    ex.getMessage());
        } else {
            log.warn(
                    "Failed to publish outbox event {} (retry {}/{}): {}",
                    event.getId(),
                    newRetryCount,
                    outboxProperties.maxRetries(),
                    ex.getMessage());
        }
    }
}
