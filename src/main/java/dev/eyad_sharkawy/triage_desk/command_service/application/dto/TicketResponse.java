package dev.eyad_sharkawy.triage_desk.command_service.application.dto;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TicketEntity;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TicketResponse(
        @Nullable UUID id,
        UUID tenantId,
        UUID customerId,
        String customerEmail,
        String subject,
        String description,
        TicketPriority priority,
        TicketStatus status,
        @Nullable UUID assignedTo,
        Instant slaDueAt,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt) {

    public static TicketResponse fromEntity(TicketEntity entity) {
        return new TicketResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getCustomerId(),
                entity.getCustomerEmail(),
                entity.getSubject(),
                entity.getDescription(),
                entity.getPriority(),
                entity.getStatus(),
                entity.getAssignedTo(),
                entity.getSlaDueAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
