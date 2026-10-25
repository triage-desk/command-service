package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.config;

import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@NullMarked
@ConfigurationProperties(prefix = "app.sla")
public record SlaProperties(@Nullable Map<TicketPriority, Integer> defaultHours) {

    public int getHoursFor(TicketPriority priority) {
        return defaultHours != null ? defaultHours.getOrDefault(priority, 72) : 72;
    }
}
