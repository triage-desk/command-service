package dev.eyad_sharkawy.triage_desk.command_service;

import dev.eyad_sharkawy.triage_desk.command_service.repository.OutboxEventRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TenantSlaPolicyRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.TicketRepository;
import dev.eyad_sharkawy.triage_desk.command_service.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CommandServiceApplicationTests {

    @MockitoBean private TenantRepository tenantRepository;
    @MockitoBean private TicketRepository ticketRepository;
    @MockitoBean private OutboxEventRepository outboxEventRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private TenantSlaPolicyRepository tenantSlaPolicyRepository;
    @MockitoBean private EntityManager entityManager;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void contextLoads() {}
}
