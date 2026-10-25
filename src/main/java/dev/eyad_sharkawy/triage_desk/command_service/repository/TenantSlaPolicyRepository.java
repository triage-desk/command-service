package dev.eyad_sharkawy.triage_desk.command_service.repository;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TenantSlaPolicyEntity;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface TenantSlaPolicyRepository extends JpaRepository<TenantSlaPolicyEntity, UUID> {
    Optional<TenantSlaPolicyEntity> findByTenantIdAndPriority(
            UUID tenantId, TicketPriority priority);

    List<TenantSlaPolicyEntity> findAllByTenantId(UUID tenantId);
}
