package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantRlsAspectTest {

    private EntityManager entityManager;
    private TenantRlsAspect aspect;
    private Query query;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);
        aspect = new TenantRlsAspect(entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldExecuteSetLocalWhenTenantContextIsSet() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        when(entityManager.createNativeQuery("SET LOCAL app.current_tenant = :tenantId"))
                .thenReturn(query);
        when(query.setParameter("tenantId", tenantId.toString())).thenReturn(query);

        aspect.applyTenantRls();

        verify(entityManager).createNativeQuery("SET LOCAL app.current_tenant = :tenantId");
        verify(query).setParameter("tenantId", tenantId.toString());
        verify(query).executeUpdate();
    }

    @Test
    void shouldSkipSetLocalWhenTenantContextIsEmpty() {
        TenantContext.clear();

        aspect.applyTenantRls();

        verify(entityManager, never()).createNativeQuery(anyString());
    }
}
