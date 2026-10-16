package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.security;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Slf4j
@Component
@NullMarked
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Pattern TENANT_URI_PATTERN =
            Pattern.compile("^/api/v1/([a-zA-Z0-9_-]+)(/.*)?$");

    private final TenantRepository tenantRepository;

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Matcher matcher = TENANT_URI_PATTERN.matcher(path);

        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        String tenantSlug = matcher.group(1);

        try {
            TenantEntity tenant =
                    tenantRepository
                            .findBySlug(tenantSlug)
                            .orElseThrow(
                                    () -> {
                                        log.warn(
                                                "Tenant resolution failed: no tenant found with"
                                                        + " slug '{}'",
                                                tenantSlug);
                                        return new TenantNotFoundException(
                                                "Tenant not found with slug: " + tenantSlug);
                                    });

            UUID tenantId = tenant.getId();
            if (tenantId == null) {
                throw new IllegalStateException("Resolved tenant ID cannot be null");
            }

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String jwtTenantIdStr = jwt.getClaimAsString("tenant_id");
                if (jwtTenantIdStr != null) {
                    UUID jwtTenantId = UUID.fromString(jwtTenantIdStr);
                    if (!jwtTenantId.equals(tenantId)) {
                        log.warn(
                                "Tenant access mismatch: JWT tenant '{}' attempted access to tenant"
                                        + " '{}' ({})",
                                jwtTenantId,
                                tenantSlug,
                                tenantId);
                        throw new TenantAccessDeniedException(
                                "Access denied: Token tenant does not match requested tenant: "
                                        + tenantSlug);
                    }
                }
            }

            log.debug("Successfully resolved tenant '{}' to ID {}", tenantSlug, tenantId);
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            exceptionResolver.resolveException(request, response, null, ex);
        } finally {
            TenantContext.clear();
        }
    }
}
