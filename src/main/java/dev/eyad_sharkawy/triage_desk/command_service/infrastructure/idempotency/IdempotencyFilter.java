package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency;

import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyConflictException;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyKeyMissingException;
import dev.eyad_sharkawy.triage_desk.common.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Slf4j
@Component
@NullMarked
@AllArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");

    private final IdempotencyService idempotencyService;

    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!MUTATING_METHODS.contains(method) || !path.startsWith("/api/v1/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn(
                    "Missing required '{}' header for {} {}", IDEMPOTENCY_KEY_HEADER, method, path);
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new IdempotencyKeyMissingException(
                            "Missing required '"
                                    + IDEMPOTENCY_KEY_HEADER
                                    + "' header for write operation"));
            return;
        }

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = idempotencyKey.trim();

        boolean isLocked = idempotencyService.acquireLock(tenantId, key);

        if (!isLocked) {
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new IdempotencyConflictException(
                            "A request with idempotency key '"
                                    + key
                                    + "' is already in progress or completed"));
            return;
        }

        try {
            filterChain.doFilter(request, response);
            if (response.getStatus() >= 200 && response.getStatus() <= 300) {
                idempotencyService.markCompleted(tenantId, key);
            } else {
                idempotencyService.releaseLock(tenantId, key);
            }
        } catch (Exception ex) {
            idempotencyService.releaseLock(tenantId, key);
            throw ex;
        }
    }
}
