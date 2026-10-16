package dev.eyad_sharkawy.triage_desk.command_service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TenantEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TicketEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.UserEntity;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.OutboxEventStatus;
import dev.eyad_sharkawy.triage_desk.command_service.domain.enums.UserRole;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEntitiesAndEnumsTest {

    @Test
    void shouldCreateTenantEntity() {
        UUID id = UUID.randomUUID();
        TenantEntity tenant =
                TenantEntity.builder()
                        .id(id)
                        .name("Acme Corp")
                        .slug("acme")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        assertThat(tenant.getId()).isEqualTo(id);
        assertThat(tenant.getName()).isEqualTo("Acme Corp");
        assertThat(tenant.getSlug()).isEqualTo("acme");
        assertThat(tenant.getCreatedAt()).isNotNull();
        assertThat(tenant.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldCreateUserEntity() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserEntity user =
                UserEntity.builder()
                        .id(id)
                        .tenantId(tenantId)
                        .keycloakId("kc-123")
                        .email("agent@acme.com")
                        .firstName("John")
                        .lastName("Doe")
                        .role(UserRole.SUPPORT_AGENT)
                        .build();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getTenantId()).isEqualTo(tenantId);
        assertThat(user.getKeycloakId()).isEqualTo("kc-123");
        assertThat(user.getEmail()).isEqualTo("agent@acme.com");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getRole()).isEqualTo(UserRole.SUPPORT_AGENT);
    }

    @Test
    void shouldCreateTicketEntityWithDefaults() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant due = Instant.now().plusSeconds(86400);

        TicketEntity ticket =
                TicketEntity.builder()
                        .id(id)
                        .tenantId(tenantId)
                        .customerId(customerId)
                        .customerEmail("customer@example.com")
                        .subject("Login issue")
                        .description("Cannot login to portal")
                        .slaDueAt(due)
                        .build();

        assertThat(ticket.getId()).isEqualTo(id);
        assertThat(ticket.getPriority()).isEqualTo(TicketPriority.NONE);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getSlaDueAt()).isEqualTo(due);
        assertThat(ticket.getAssignedTo()).isNull();
    }

    @Test
    void shouldCreateOutboxEventEntityWithDefaults() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();

        OutboxEventEntity event =
                OutboxEventEntity.builder()
                        .id(id)
                        .tenantId(tenantId)
                        .aggregateType("Ticket")
                        .aggregateId(aggregateId)
                        .eventType("TicketCreatedEvent")
                        .payload("{\"ticketId\":\"" + aggregateId + "\"}")
                        .build();

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getPayload()).contains(aggregateId.toString());
    }

    @Test
    void shouldVerifyAllUserRoleEnumValues() {
        assertThat(UserRole.values())
                .containsExactlyInAnyOrder(
                        UserRole.TENANT_OWNER,
                        UserRole.TENANT_ADMIN,
                        UserRole.SUPPORT_AGENT,
                        UserRole.CUSTOMER_USER);
    }

    @Test
    void shouldVerifyAllOutboxEventStatusEnumValues() {
        assertThat(OutboxEventStatus.values())
                .containsExactlyInAnyOrder(
                        OutboxEventStatus.PENDING,
                        OutboxEventStatus.PUBLISHED,
                        OutboxEventStatus.FAILED);
    }
}
