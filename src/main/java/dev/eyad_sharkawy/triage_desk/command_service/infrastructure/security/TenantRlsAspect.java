package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.security;

import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order
@NullMarked
@RequiredArgsConstructor
public class TenantRlsAspect {

    private final EntityManager entityManager;

    @Before(
            "@annotation(org.springframework.transaction.annotation.Transactional) || "
                    + "@within(org.springframework.transaction.annotation.Transactional)")
    public void applyTenantRls() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            log.trace("Applying PostgreSQL RLS: SET LOCAL app.current_tenant = '{}'", tenantId);
            entityManager
                    .createNativeQuery("SET LOCAL app.current_tenant = :tenantId")
                    .setParameter("tenantId", tenantId.toString())
                    .executeUpdate();
        } else {
            log.trace("TenantContext is empty; skipping SET LOCAL app.current_tenant");
        }
    }
}
