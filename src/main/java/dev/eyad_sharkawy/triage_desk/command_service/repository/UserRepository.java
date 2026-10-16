package dev.eyad_sharkawy.triage_desk.command_service.repository;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    Optional<UserEntity> findByKeycloakId(String keycloakId);
}
