package dev.eyad_sharkawy.triage_desk.command_service.repository;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.OutboxEventEntity;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@NullMarked
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(
            value =
                    """
                    SELECT * FROM outbox_events
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<OutboxEventEntity> findPendingEventsForProcessing(@Param("batchSize") int batchSize);
}
