package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TenantEntity;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantRepository;
import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantAccessDeniedException;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerExceptionResolver;

class TenantResolutionFilterTest {

    private TenantRepository tenantRepository;
    private HandlerExceptionResolver exceptionResolver;
    private TenantResolutionFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepository.class);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filter = new TenantResolutionFilter(tenantRepository, exceptionResolver);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenUriDoesNotMatchTenantPattern() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tenantRepository, never()).findBySlug(any());
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldResolveTenantAndSetContextWhenUriMatches() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = TenantEntity.builder().id(tenantId).name("Acme").slug("acme").build();

        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(TenantContext.getTenantId()).isNull(); // Cleared in finally
    }

    @Test
    void shouldDelegateToExceptionResolverWhenTenantNotFound()
            throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/unknown/tickets");
        when(tenantRepository.findBySlug("unknown")).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver)
                .resolveException(
                        eq(request), eq(response), isNull(), any(TenantNotFoundException.class));
        verify(filterChain, never()).doFilter(request, response);
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void shouldValidateMatchingJwtTenantId() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        TenantEntity tenant = TenantEntity.builder().id(tenantId).name("Acme").slug("acme").build();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn(tenantId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(exceptionResolver, never()).resolveException(any(), any(), any(), any());
    }

    @Test
    void shouldRejectWhenJwtTenantIdMismatches() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();
        TenantEntity tenant = TenantEntity.builder().id(tenantId).name("Acme").slug("acme").build();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("tenant_id")).thenReturn(differentTenantId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(request.getRequestURI()).thenReturn("/api/v1/acme/tickets");
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(tenant));

        filter.doFilter(request, response, filterChain);

        verify(exceptionResolver)
                .resolveException(
                        eq(request),
                        eq(response),
                        isNull(),
                        any(TenantAccessDeniedException.class));
        verify(filterChain, never()).doFilter(request, response);
    }
}
