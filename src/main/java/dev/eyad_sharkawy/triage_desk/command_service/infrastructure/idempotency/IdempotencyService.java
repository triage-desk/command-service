package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.idempotency.ttl:60m}")
    private final Duration ttl;

    public boolean acquireLock(UUID tenantId, String idempotencyKey) {
        String redisKey = buildKey(tenantId, idempotencyKey);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug(
                    "Acquired idempotency lock for key '{}' (tenant: {})",
                    idempotencyKey,
                    tenantId);
            return true;
        }

        log.warn(
                "Idempotency conflict: key '{}' already exists for tenant {}",
                idempotencyKey,
                tenantId);
        return false;
    }

    public void markCompleted(UUID tenantId, String idempotencyKey) {
        String redisKey = buildKey(tenantId, idempotencyKey);
        redisTemplate.opsForValue().set(redisKey, COMPLETED, ttl);
        log.debug(
                "Marked idempotency key '{}' as COMPLETED (tenant: {})", idempotencyKey, tenantId);
    }

    public void releaseLock(UUID tenantId, String idempotencyKey) {
        String redisKey = buildKey(tenantId, idempotencyKey);
        redisTemplate.delete(redisKey);
        log.debug("Released idempotency lock for key '{}' (tenant: {})", idempotencyKey, tenantId);
    }

    private String buildKey(UUID tenantId, String idempotencyKey) {
        return "idempotency:" + tenantId + ":" + idempotencyKey;
    }
}
