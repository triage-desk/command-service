package dev.eyad_sharkawy.triage_desk.command_service.domain.entity;

import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@NullMarked
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private @Nullable UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", nullable = false)
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "priority", nullable = false, columnDefinition = "ticket_priority")
    private TicketPriority priority = TicketPriority.NONE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, columnDefinition = "ticket_status")
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "assigned_to")
    private @Nullable UUID assignedTo;

    @Column(name = "assigned_at")
    private @Nullable Instant assignedAt;

    @Column(name = "resolved_by")
    private @Nullable UUID resolvedBy;

    @Column(name = "resolved_at")
    private @Nullable Instant resolvedAt;

    @Column(name = "resolution_summary")
    private @Nullable String resolutionSummary;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Version
    @Column(name = "version", nullable = false)
    private @Nullable Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;
}
