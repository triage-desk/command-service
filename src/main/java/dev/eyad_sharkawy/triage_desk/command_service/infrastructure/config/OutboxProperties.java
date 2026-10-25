package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NullMarked
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(String topic, int batchSize, long pollingDelayMs, int maxRetries) {
    public OutboxProperties {
        topic = (topic != null && !topic.isBlank()) ? topic : "triage-desk.command.tickets";
        batchSize = batchSize > 0 ? batchSize : 50;
        pollingDelayMs = pollingDelayMs > 0 ? pollingDelayMs : 500;
        maxRetries = maxRetries > 0 ? maxRetries : 5;
    }
}
