package dev.eyad_sharkawy.triage_desk.command_service.domain.service;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TenantSlaPolicyEntity;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.SlaProperties;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantSlaPolicyRepository;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@NullMarked
@RequiredArgsConstructor
public class SlaCalculatorService {

    private static final String REDIS_SLA_KEY_PREFIX = "sla:policy:";
    private final TenantSlaPolicyRepository policyRepository;
    private final SlaProperties slaProperties;
    private final StringRedisTemplate redisTemplate;

    public Instant calculateSlaDueAt(UUID tenantId, TicketPriority priority, Instant baseTime) {
        int hours = resolveDurationHours(tenantId, priority);
        return baseTime.plus(Duration.ofHours(hours));
    }

    private int resolveDurationHours(UUID tenantId, TicketPriority priority) {
        String cacheKey = REDIS_SLA_KEY_PREFIX + tenantId;
        String field = priority.name();

        try {
            Object cachedHours = redisTemplate.opsForHash().get(cacheKey, field);
            if (cachedHours != null) {
                return Integer.parseInt(cachedHours.toString());
            }
        } catch (Exception ex) {
            log.warn(
                    "Failed to read SLA policy from Redis cache for tenant {}: {}",
                    tenantId,
                    ex.getMessage());
        }

        int resolvedHours =
                policyRepository
                        .findByTenantIdAndPriority(tenantId, priority)
                        .map(TenantSlaPolicyEntity::getDurationHours)
                        .orElseGet(() -> slaProperties.getHoursFor(priority));

        try {
            redisTemplate.opsForHash().put(cacheKey, field, String.valueOf(resolvedHours));
            redisTemplate.expire(cacheKey, Duration.ofDays(1));
        } catch (Exception ex) {
            log.warn(
                    "Failed to cache SLA policy in Redis for tenant {}: {}",
                    tenantId,
                    ex.getMessage());
        }

        return resolvedHours;
    }

    public void evictTenantSlaCache(UUID tenantId) {
        try {
            redisTemplate.delete(REDIS_SLA_KEY_PREFIX + tenantId);
        } catch (Exception ex) {
            log.warn(
                    "Failed to evict SLA policy cache for tenant {}: {}",
                    tenantId,
                    ex.getMessage());
        }
    }
}
