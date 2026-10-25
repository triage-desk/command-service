package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.CreateTicketRequest;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.TicketResponse;
import dev.eyad_sharkawy.triage_desk.command_service.application.service.TicketCommandService;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config.JacksonConfig;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import dev.eyad_sharkawy.triage_desk.common.domain.TicketStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TicketCommandControllerTest {

    @Mock private TicketCommandService ticketCommandService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    private final UUID ticketId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TicketCommandController controller = new TicketCommandController(ticketCommandService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        Jwt jwt =
                new Jwt(
                        "mock-token",
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        Map.of("alg", "none"),
                        Map.of("sub", "user-123", "email", "customer@example.com"));
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "POST /api/v1/{tenantSlug}/tickets returns 201 Created with Location header on valid"
                    + " request")
    void shouldCreateTicketSuccessfully() throws Exception {
        TicketResponse response =
                new TicketResponse(
                        ticketId,
                        tenantId,
                        customerId,
                        "customer@example.com",
                        "Cannot access dashboard",
                        "Dashboard gives 500 error",
                        TicketPriority.HIGH,
                        TicketStatus.OPEN,
                        null,
                        Instant.now().plusSeconds(28800),
                        Instant.now(),
                        Instant.now());

        when(ticketCommandService.createTicket(any(CreateTicketRequest.class), any()))
                .thenReturn(response);

        CreateTicketRequest request =
                new CreateTicketRequest(
                        "Cannot access dashboard",
                        "Dashboard gives 500 error",
                        TicketPriority.HIGH,
                        null);

        mockMvc.perform(
                        post("/api/v1/acme-corp/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/acme-corp/tickets/" + ticketId))
                .andExpect(jsonPath("$.id").value(ticketId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.subject").value("Cannot access dashboard"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(ticketCommandService).createTicket(any(CreateTicketRequest.class), any());
    }

    @Test
    @DisplayName("POST /api/v1/{tenantSlug}/tickets returns 400 Bad Request when subject is blank")
    void shouldReturnBadRequestWhenSubjectIsBlank() throws Exception {
        String invalidPayload =
                """
                {
                    "subject": "   ",
                    "description": "Valid description",
                    "priority": "LOW"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/acme-corp/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("subject"));
    }

    @Test
    @DisplayName(
            "POST /api/v1/{tenantSlug}/tickets returns 400 Bad Request when customerEmail is"
                    + " invalid")
    void shouldReturnBadRequestWhenEmailFormatIsInvalid() throws Exception {
        String invalidPayload =
                """
                {
                    "subject": "Valid Subject",
                    "description": "Valid description",
                    "priority": "LOW",
                    "customerEmail": "not-an-email"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/acme-corp/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("customerEmail"));
    }
}
