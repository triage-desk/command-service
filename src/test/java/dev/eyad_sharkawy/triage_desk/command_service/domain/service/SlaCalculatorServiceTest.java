package dev.eyad_sharkawy.triage_desk.command_service.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.command_service.domain.entity.TenantSlaPolicyEntity;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.SlaProperties;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantSlaPolicyRepository;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class SlaCalculatorServiceTest {

    @Mock private TenantSlaPolicyRepository policyRepository;

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private HashOperations<String, Object, Object> hashOperations;

    private SlaProperties slaProperties;
    private SlaCalculatorService slaCalculatorService;

    private final UUID tenantId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-25T10:00:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        slaProperties =
                new SlaProperties(
                        Map.of(
                                TicketPriority.URGENT, 4,
                                TicketPriority.HIGH, 8,
                                TicketPriority.MEDIUM, 24,
                                TicketPriority.LOW, 48,
                                TicketPriority.NONE, 72));
        slaCalculatorService =
                new SlaCalculatorService(policyRepository, slaProperties, redisTemplate);
    }

    @Test
    @DisplayName("Should return SLA from Redis cache when present")
    void shouldReturnSlaFromRedisCache() {
        when(hashOperations.get("sla:policy:" + tenantId, "URGENT")).thenReturn("2");

        Instant slaDueAt =
                slaCalculatorService.calculateSlaDueAt(tenantId, TicketPriority.URGENT, baseTime);

        assertThat(slaDueAt).isEqualTo(baseTime.plus(Duration.ofHours(2)));
    }

    @Test
    @DisplayName("Should query DB and cache result in Redis when Redis misses")
    void shouldQueryDbAndCacheInRedisOnCacheMiss() {
        when(hashOperations.get("sla:policy:" + tenantId, "HIGH")).thenReturn(null);
        TenantSlaPolicyEntity customPolicy =
                TenantSlaPolicyEntity.builder()
                        .tenantId(tenantId)
                        .priority(TicketPriority.HIGH)
                        .durationHours(6)
                        .build();
        when(policyRepository.findByTenantIdAndPriority(tenantId, TicketPriority.HIGH))
                .thenReturn(Optional.of(customPolicy));

        Instant slaDueAt =
                slaCalculatorService.calculateSlaDueAt(tenantId, TicketPriority.HIGH, baseTime);

        assertThat(slaDueAt).isEqualTo(baseTime.plus(Duration.ofHours(6)));
        verify(hashOperations).put("sla:policy:" + tenantId, "HIGH", "6");
        verify(redisTemplate).expire(eq("sla:policy:" + tenantId), any(Duration.class));
    }

    @Test
    @DisplayName("Should fall back to SlaProperties when neither Redis nor DB has a policy")
    void shouldFallbackToPropertiesWhenNoCustomPolicy() {
        when(hashOperations.get("sla:policy:" + tenantId, "MEDIUM")).thenReturn(null);
        when(policyRepository.findByTenantIdAndPriority(tenantId, TicketPriority.MEDIUM))
                .thenReturn(Optional.empty());

        Instant slaDueAt =
                slaCalculatorService.calculateSlaDueAt(tenantId, TicketPriority.MEDIUM, baseTime);

        assertThat(slaDueAt).isEqualTo(baseTime.plus(Duration.ofHours(24)));
        verify(hashOperations).put("sla:policy:" + tenantId, "MEDIUM", "24");
    }

    @Test
    @DisplayName("Should gracefully handle Redis failure and fall back to DB/properties")
    void shouldHandleRedisFailureGracefully() {
        when(hashOperations.get(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(policyRepository.findByTenantIdAndPriority(tenantId, TicketPriority.URGENT))
                .thenReturn(Optional.empty());

        Instant slaDueAt =
                slaCalculatorService.calculateSlaDueAt(tenantId, TicketPriority.URGENT, baseTime);

        assertThat(slaDueAt).isEqualTo(baseTime.plus(Duration.ofHours(4)));
    }

    @Test
    @DisplayName("Should evict tenant SLA cache")
    void shouldEvictTenantSlaCache() {
        slaCalculatorService.evictTenantSlaCache(tenantId);

        verify(redisTemplate).delete("sla:policy:" + tenantId);
    }
}
