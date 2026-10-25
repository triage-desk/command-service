package dev.eyad_sharkawy.triage_desk.command_service.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.CreateTicketRequest;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.TicketResponse;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TicketEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.UserEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.OutboxEventStatus;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.UserRole;
import dev.eyad_sharkawy.triage_desk.command_service.domain.service.SlaCalculatorService;
import dev.eyad_sharkawy.triage_desk.command_service.repository.OutboxEventRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TicketRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.UserRepository;
import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import dev.eyad_sharkawy.triage_desk.common.events.TicketCreatedEvent;
import dev.eyad_sharkawy.triage_desk.common.exceptions.ResourceNotFoundException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UserRepository userRepository;
    private final SlaCalculatorService slaCalculatorService;
    private final ObjectMapper objectMapper;

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, Jwt jwt) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = Instant.now();

        UserEntity customer = resolveCustomer(tenantId, request.customerEmail(), jwt);
        TicketPriority priority =
                request.priority() != null ? request.priority() : TicketPriority.NONE;

        Instant slaDueAt = slaCalculatorService.calculateSlaDueAt(tenantId, priority, now);

        TicketEntity ticket =
                TicketEntity.builder()
                        .tenantId(tenantId)
                        .customerId(
                                Objects.requireNonNull(
                                        customer.getId(), "Customer ID must not be null"))
                        .customerEmail(customer.getEmail())
                        .subject(request.subject())
                        .description(request.description())
                        .priority(priority)
                        .status(TicketStatus.OPEN)
                        .slaDueAt(slaDueAt)
                        .build();

        ticket = ticketRepository.save(ticket);
        UUID ticketId = Objects.requireNonNull(ticket.getId(), "Saved ticket ID must not be null");

        TicketCreatedEvent event =
                new TicketCreatedEvent(
                        ticketId,
                        ticket.getTenantId(),
                        ticket.getCustomerId(),
                        ticket.getCustomerEmail(),
                        ticket.getSubject(),
                        ticket.getPriority().name(),
                        ticket.getCreatedAt() != null ? ticket.getCreatedAt() : now,
                        ticket.getSlaDueAt());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize TicketCreatedEvent payload", e);
        }

        OutboxEventEntity outboxEvent =
                OutboxEventEntity.builder()
                        .tenantId(tenantId)
                        .aggregateType("Ticket")
                        .aggregateId(ticketId)
                        .eventType("TicketCreated")
                        .payload(payloadJson)
                        .status(OutboxEventStatus.PENDING)
                        .retryCount(0)
                        .build();

        outboxEventRepository.save(outboxEvent);

        log.info(
                "Ticket {} created with outbox event {} for tenant {}",
                ticketId,
                outboxEvent.getId(),
                tenantId);

        return TicketResponse.fromEntity(ticket);
    }

    private UserEntity resolveCustomer(UUID tenantId, @Nullable String customerEmail, Jwt jwt) {
        if (customerEmail != null && !customerEmail.isBlank()) {
            String cleanEmail = sanitizeEmail(customerEmail);
            return userRepository
                    .findByTenantIdAndEmail(tenantId, cleanEmail)
                    .orElseThrow(
                            () ->
                                    new ResourceNotFoundException(
                                            "Customer not found with email: " + cleanEmail));
        }

        String keycloakId = jwt.getSubject();
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("JWT subject (Keycloak ID) must not be null or blank");
        }

        return userRepository
                .findByKeycloakId(keycloakId)
                .orElseGet(
                        () -> {
                            String email = extractEmailFromJwt(jwt);
                            String firstName =
                                    jwt.getClaimAsString("given_name") != null
                                            ? jwt.getClaimAsString("given_name")
                                            : "Customer";
                            String lastName =
                                    jwt.getClaimAsString("family_name") != null
                                            ? jwt.getClaimAsString("family_name")
                                            : "User";

                            return userRepository.save(
                                    UserEntity.builder()
                                            .tenantId(tenantId)
                                            .keycloakId(keycloakId)
                                            .email(email)
                                            .firstName(firstName)
                                            .lastName(lastName)
                                            .role(UserRole.CUSTOMER_USER)
                                            .build());
                        });
    }

    private String extractEmailFromJwt(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "JWT contains neither 'email' nor 'preferred_username' claim");
        }
        return sanitizeEmail(email);
    }

    private String sanitizeEmail(String rawEmail) {
        int colonIdx = rawEmail.indexOf(':');
        if (colonIdx != -1 && colonIdx < rawEmail.length() - 1) {
            return rawEmail.substring(colonIdx + 1).trim();
        }
        return rawEmail.trim();
    }
}
