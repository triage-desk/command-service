package dev.eyad_sharkawy.triage_desk.command_service.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.CreateTicketRequest;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.TicketResponse;
import dev.eyad_sharkawy.triage_desk.command_service.application.event.OutboxEventReadyEvent;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TicketEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.UserEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.OutboxEventStatus;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.UserRole;
import dev.eyad_sharkawy.triage_desk.command_service.domain.service.SlaCalculatorService;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.JacksonConfig;
import dev.eyad_sharkawy.triage_desk.command_service.repository.OutboxEventRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TicketRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.UserRepository;
import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import dev.eyad_sharkawy.triage_desk.common.events.TicketCreatedEvent;
import dev.eyad_sharkawy.triage_desk.common.exceptions.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TicketCommandServiceTest {

    @Mock private TicketRepository ticketRepository;

    @Mock private OutboxEventRepository outboxEventRepository;

    @Mock private UserRepository userRepository;

    @Mock private SlaCalculatorService slaCalculatorService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private Jwt jwt;

    @Captor private ArgumentCaptor<TicketEntity> ticketCaptor;

    @Captor private ArgumentCaptor<OutboxEventEntity> outboxEventCaptor;

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private TicketCommandService ticketCommandService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantId);
        ticketCommandService =
                new TicketCommandService(
                        ticketRepository,
                        outboxEventRepository,
                        userRepository,
                        slaCalculatorService,
                        objectMapper,
                        eventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should create ticket and persist outbox event for existing customer")
    void shouldCreateTicketAndOutboxForExistingCustomer() throws Exception {
        when(jwt.getSubject()).thenReturn("kc-user-123");
        UserEntity existingCustomer =
                UserEntity.builder()
                        .id(userId)
                        .tenantId(tenantId)
                        .keycloakId("kc-user-123")
                        .email("customer@example.com")
                        .firstName("Jane")
                        .lastName("Doe")
                        .role(UserRole.CUSTOMER_USER)
                        .build();
        when(userRepository.findByKeycloakId("kc-user-123"))
                .thenReturn(Optional.of(existingCustomer));

        Instant slaTime = Instant.now().plusSeconds(14400);
        when(slaCalculatorService.calculateSlaDueAt(
                        eq(tenantId), eq(TicketPriority.HIGH), any(Instant.class)))
                .thenReturn(slaTime);

        when(ticketRepository.save(any(TicketEntity.class)))
                .thenAnswer(
                        invocation -> {
                            TicketEntity entity = invocation.getArgument(0);
                            entity.setId(ticketId);
                            entity.setCreatedAt(Instant.now());
                            return entity;
                        });

        UUID outboxId = UUID.randomUUID();
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(
                        invocation -> {
                            OutboxEventEntity entity = invocation.getArgument(0);
                            entity.setId(outboxId);
                            return entity;
                        });

        CreateTicketRequest request =
                new CreateTicketRequest("Login Issue", "Cannot sign in", TicketPriority.HIGH, null);

        TicketResponse response = ticketCommandService.createTicket(request, jwt);

        assertThat(response.id()).isEqualTo(ticketId);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.customerId()).isEqualTo(userId);
        assertThat(response.customerEmail()).isEqualTo("customer@example.com");
        assertThat(response.subject()).isEqualTo("Login Issue");
        assertThat(response.description()).isEqualTo("Cannot sign in");
        assertThat(response.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(response.slaDueAt()).isEqualTo(slaTime);

        verify(ticketRepository).save(ticketCaptor.capture());
        TicketEntity savedTicket = ticketCaptor.getValue();
        assertThat(savedTicket.getTenantId()).isEqualTo(tenantId);
        assertThat(savedTicket.getStatus()).isEqualTo(TicketStatus.OPEN);

        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEventEntity savedOutbox = outboxEventCaptor.getValue();
        assertThat(savedOutbox.getTenantId()).isEqualTo(tenantId);
        assertThat(savedOutbox.getAggregateType()).isEqualTo("Ticket");
        assertThat(savedOutbox.getAggregateId()).isEqualTo(ticketId);
        assertThat(savedOutbox.getEventType()).isEqualTo("TicketCreated");
        assertThat(savedOutbox.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        verify(eventPublisher).publishEvent(any(OutboxEventReadyEvent.class));

        TicketCreatedEvent eventPayload =
                objectMapper.readValue(savedOutbox.getPayload(), TicketCreatedEvent.class);
        assertThat(eventPayload.ticketId()).isEqualTo(ticketId);
        assertThat(eventPayload.tenantId()).isEqualTo(tenantId);
        assertThat(eventPayload.customerId()).isEqualTo(userId);
        assertThat(eventPayload.customerEmail()).isEqualTo("customer@example.com");
        assertThat(eventPayload.priority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Should JIT provision customer when not found in database and strip tenant prefix")
    void shouldJitProvisionCustomerAndStripPrefix() {
        when(jwt.getSubject()).thenReturn("kc-user-456");
        when(jwt.getClaimAsString("email")).thenReturn(null);
        when(jwt.getClaimAsString("preferred_username")).thenReturn("acme:newuser@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("New");
        when(jwt.getClaimAsString("family_name")).thenReturn("User");

        when(userRepository.findByKeycloakId("kc-user-456")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class)))
                .thenAnswer(
                        invocation -> {
                            UserEntity entity = invocation.getArgument(0);
                            entity.setId(userId);
                            return entity;
                        });

        when(slaCalculatorService.calculateSlaDueAt(
                        eq(tenantId), eq(TicketPriority.NONE), any(Instant.class)))
                .thenReturn(Instant.now().plusSeconds(259200));

        when(ticketRepository.save(any(TicketEntity.class)))
                .thenAnswer(
                        invocation -> {
                            TicketEntity entity = invocation.getArgument(0);
                            entity.setId(ticketId);
                            entity.setCreatedAt(Instant.now());
                            return entity;
                        });

        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(
                        invocation -> {
                            OutboxEventEntity entity = invocation.getArgument(0);
                            entity.setId(UUID.randomUUID());
                            return entity;
                        });

        CreateTicketRequest request =
                new CreateTicketRequest("General inquiry", "How does pricing work?", null, null);

        TicketResponse response = ticketCommandService.createTicket(request, jwt);

        assertThat(response.customerEmail()).isEqualTo("newuser@example.com");
        verify(userRepository).save(any(UserEntity.class));
        verify(eventPublisher).publishEvent(any(OutboxEventReadyEvent.class));
    }

    @Test
    @DisplayName(
            "Should throw ResourceNotFoundException when agent specifies non-existent"
                    + " customerEmail")
    void shouldThrowWhenAgentSpecifiesNonExistentCustomer() {
        when(userRepository.findByTenantIdAndEmail(tenantId, "unknown@example.com"))
                .thenReturn(Optional.empty());

        CreateTicketRequest request =
                new CreateTicketRequest(
                        "Billing bug",
                        "Incorrect charge",
                        TicketPriority.URGENT,
                        "unknown@example.com");

        assertThatThrownBy(() -> ticketCommandService.createTicket(request, jwt))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer not found with email: unknown@example.com");
    }
}
