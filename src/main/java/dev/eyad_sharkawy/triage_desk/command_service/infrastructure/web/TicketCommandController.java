package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.web;

import dev.eyad_sharkawy.triage_desk.command_service.application.dto.CreateTicketRequest;
import dev.eyad_sharkawy.triage_desk.command_service.application.dto.TicketResponse;
import dev.eyad_sharkawy.triage_desk.command_service.application.service.TicketCommandService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/{tenantSlug}/tickets")
@NullMarked
@RequiredArgsConstructor
public class TicketCommandController {

    private final TicketCommandService ticketCommandService;

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @PathVariable String tenantSlug,
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        log.info(
                "Received ticket creation request for tenant '{}', customerEmail='{}',"
                        + " priority='{}'",
                tenantSlug,
                request.customerEmail(),
                request.priority());

        TicketResponse response = ticketCommandService.createTicket(request, jwt);
        URI location = URI.create("/api/v1/" + tenantSlug + "/tickets/" + response.id());

        return ResponseEntity.created(location).body(response);
    }
}
