package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyConflictException;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyKeyMissingException;
import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerExceptionResolver;

class IdempotencyFilterTest {

    private IdempotencyService idempotencyService;
    private HandlerExceptionResolver exceptionResolver;
    private IdempotencyFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        idempotencyService = mock(IdempotencyService.class);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filter = new IdempotencyFilter(idempotencyService, exceptionResolver);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSkipNonMutatingMethods() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(idempotencyService, never()).acquireLock(any(), any());
    }

    @Test
    void shouldRejectWhenIdempotencyKeyMissingOnPost() throws ServletException, IOException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(request.getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver)
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        any(IdempotencyKeyMissingException.class));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectWhenLockCannotBeAcquired() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(request.getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER)).thenReturn("dup-key");
        when(idempotencyService.acquireLock(tenantId, "dup-key")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver)
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        any(IdempotencyConflictException.class));
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldProceedAndMarkCompletedOnSuccess() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(request.getHeader(IdempotencyFilter.IDEMPOTENCY_KEY_HEADER)).thenReturn("valid-key");
        when(idempotencyService.acquireLock(tenantId, "valid-key")).thenReturn(true);
        when(response.getStatus()).thenReturn(201);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(idempotencyService).markCompleted(tenantId, "valid-key");
    }
}
