package dev.eyad_sharkawy.triage_desk.command_service;

import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CommandServiceApplicationTests {

    @MockitoBean private TenantRepository tenantRepository;
    @MockitoBean private EntityManager entityManager;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private StringRedisTemplate redisTemplate;

    @Test
    void contextLoads() {}
}
