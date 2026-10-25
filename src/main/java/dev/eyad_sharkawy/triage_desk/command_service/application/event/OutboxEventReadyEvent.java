package dev.eyad_sharkawy.triage_desk.command_service.application.event;

import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record OutboxEventReadyEvent(UUID outboxEventId) {}
