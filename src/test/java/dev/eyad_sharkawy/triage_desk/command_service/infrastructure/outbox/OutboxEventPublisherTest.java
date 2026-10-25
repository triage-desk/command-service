package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.OutboxEventStatus;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.OutboxProperties;
import dev.eyad_sharkawy.triage_desk.command_service.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@NullMarked
class OutboxEventPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxProperties outboxProperties;
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxProperties = new OutboxProperties("triage-desk.command.tickets", 50, 500, 5);
        publisher =
                new OutboxEventPublisher(outboxEventRepository, kafkaTemplate, outboxProperties);
    }

    @Test
    @DisplayName(
            "should successfully publish pending events to Kafka with proper headers and partition"
                    + " key")
    @SuppressWarnings("unchecked")
    void shouldPublishPendingEventsSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(tenantId)
                        .aggregateType("Ticket")
                        .aggregateId(ticketId)
                        .eventType("TicketCreatedEvent")
                        .payload("{\"ticketId\":\"" + ticketId + "\"}")
                        .status(OutboxEventStatus.PENDING)
                        .build();

        when(outboxEventRepository.findPendingEventsForProcessing(50)).thenReturn(List.of(event));

        SendResult<String, String> sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publishPendingEvents();

        ArgumentCaptor<ProducerRecord<String, String>> recordCaptor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());

        ProducerRecord<String, String> producerRecord = recordCaptor.getValue();
        assertThat(producerRecord.topic()).isEqualTo("triage-desk.command.tickets");
        assertThat(producerRecord.key()).isEqualTo(ticketId.toString());
        assertThat(producerRecord.value()).isEqualTo("{\"ticketId\":\"" + ticketId + "\"}");

        Header sourceHeader = producerRecord.headers().lastHeader("X-Source-Service");
        assertThat(sourceHeader).isNotNull();
        assertThat(new String(sourceHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo("triage-command-service");

        Header tenantHeader = producerRecord.headers().lastHeader("X-Tenant-ID");
        assertThat(tenantHeader).isNotNull();
        assertThat(new String(tenantHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo(tenantId.toString());

        Header eventTypeHeader = producerRecord.headers().lastHeader("X-Event-Type");
        assertThat(eventTypeHeader).isNotNull();
        assertThat(new String(eventTypeHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo("TicketCreatedEvent");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("should do nothing when no pending outbox events exist")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findPendingEventsForProcessing(50))
                .thenReturn(Collections.emptyList());

        publisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("should increment retry count and keep PENDING status on Kafka send failure")
    @SuppressWarnings("unchecked")
    void shouldHandleKafkaSendFailureAndIncrementRetryCount() {
        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(UUID.randomUUID())
                        .aggregateType("Ticket")
                        .aggregateId(UUID.randomUUID())
                        .eventType("TicketCreatedEvent")
                        .payload("{}")
                        .status(OutboxEventStatus.PENDING)
                        .retryCount(1)
                        .build();

        when(outboxEventRepository.findPendingEventsForProcessing(50)).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unreachable"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getErrorMessage()).contains("Kafka broker unreachable");
        assertThat(event.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("should transition event to FAILED when max retries is exceeded")
    @SuppressWarnings("unchecked")
    void shouldMarkEventAsFailedWhenMaxRetriesExceeded() {
        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(UUID.randomUUID())
                        .aggregateType("Ticket")
                        .aggregateId(UUID.randomUUID())
                        .eventType("TicketCreatedEvent")
                        .payload("{}")
                        .status(OutboxEventStatus.PENDING)
                        .retryCount(4)
                        .build();

        when(outboxEventRepository.findPendingEventsForProcessing(50)).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Persistent broker error"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getErrorMessage()).contains("Persistent broker error");
    }

    @Test
    @DisplayName("should handle TimeoutException and increment retry count")
    @SuppressWarnings("unchecked")
    void shouldHandleTimeoutException() throws Exception {
        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(UUID.randomUUID())
                        .aggregateType("Ticket")
                        .aggregateId(UUID.randomUUID())
                        .eventType("TicketCreatedEvent")
                        .payload("{}")
                        .status(OutboxEventStatus.PENDING)
                        .retryCount(0)
                        .build();

        when(outboxEventRepository.findPendingEventsForProcessing(50)).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> timeoutFuture = mock(CompletableFuture.class);
        when(timeoutFuture.get(5, TimeUnit.SECONDS))
                .thenThrow(new TimeoutException("Timed out waiting for broker ACK"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(timeoutFuture);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).isEqualTo("Timed out waiting for broker ACK");
    }

    @Test
    @DisplayName("should handle InterruptedException and restore interrupted thread flag")
    @SuppressWarnings("unchecked")
    void shouldHandleInterruptedExceptionAndRestoreFlag() throws Exception {
        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(UUID.randomUUID())
                        .aggregateType("Ticket")
                        .aggregateId(UUID.randomUUID())
                        .eventType("TicketCreatedEvent")
                        .payload("{}")
                        .status(OutboxEventStatus.PENDING)
                        .retryCount(0)
                        .build();

        when(outboxEventRepository.findPendingEventsForProcessing(50)).thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> interruptedFuture =
                mock(CompletableFuture.class);
        when(interruptedFuture.get(5, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("Thread interrupted"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(interruptedFuture);

        publisher.publishPendingEvents();

        assertThat(Thread.interrupted()).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }
}
