package dev.eyad_sharkawy.triage_desk.command_service.application.dto;

import dev.eyad_sharkawy.triage_desk.common.domain.TicketPriority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CreateTicketRequest(
        @NotBlank(message = "Subject is required")
                @Size(max = 255, message = "Subject cannot exceed 255 characters")
                String subject,
        @NotBlank(message = "Description is required") String description,
        TicketPriority priority,
        @Nullable @Email(message = "Invalid customer email format") String customerEmail) {
    public CreateTicketRequest {
        if (priority == null) {
            priority = TicketPriority.NONE;
        }
    }
}
