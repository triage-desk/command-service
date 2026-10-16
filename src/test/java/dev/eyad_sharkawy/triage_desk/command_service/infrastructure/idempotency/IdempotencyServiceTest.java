package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private IdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new IdempotencyService(redisTemplate, Duration.ofMinutes(60));
    }

    @Test
    void shouldAcquireLockWhenKeyDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        String key = "test-key-1";
        String redisKey = "idempotency:" + tenantId + ":" + key;

        when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        boolean acquired = service.acquireLock(tenantId, key);

        assertThat(acquired).isTrue();
    }

    @Test
    void shouldFailToAcquireLockWhenKeyAlreadyExists() {
        UUID tenantId = UUID.randomUUID();
        String key = "test-key-2";
        String redisKey = "idempotency:" + tenantId + ":" + key;

        when(valueOperations.setIfAbsent(eq(redisKey), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);

        boolean acquired = service.acquireLock(tenantId, key);

        assertThat(acquired).isFalse();
    }

    @Test
    void shouldMarkCompleted() {
        UUID tenantId = UUID.randomUUID();
        String key = "test-key-3";
        String redisKey = "idempotency:" + tenantId + ":" + key;

        service.markCompleted(tenantId, key);

        verify(valueOperations).set(eq(redisKey), eq("COMPLETED"), any(Duration.class));
    }

    @Test
    void shouldReleaseLock() {
        UUID tenantId = UUID.randomUUID();
        String key = "test-key-4";
        String redisKey = "idempotency:" + tenantId + ":" + key;

        service.releaseLock(tenantId, key);

        verify(redisTemplate).delete(redisKey);
    }
}
