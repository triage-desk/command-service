package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.eyad_sharkawy.triage_desk.common.exceptions.ResourceNotFoundException;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantAccessDeniedException;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldHandleTenantNotFound() {
        TenantNotFoundException ex = new TenantNotFoundException("Tenant not found");

        ProblemDetail problem = handler.handleTenantNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Tenant not found");
    }

    @Test
    void shouldHandleTenantAccessDenied() {
        TenantAccessDeniedException ex = new TenantAccessDeniedException("Access denied");

        ProblemDetail problem = handler.handleTenantAccessDenied(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail()).isEqualTo("Access denied");
    }

    @Test
    void shouldHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

        ProblemDetail problem = handler.handleResourceNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Resource not found");
    }

    @Test
    void shouldHandleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError =
                new FieldError(
                        "ticket", "subject", "invalid", false, null, null, "Subject is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ProblemDetail problem = handler.handleValidationException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Validation failed");
        assertThat(problem.getProperties()).containsKey("errors");
    }

    @Test
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");

        ProblemDetail problem = handler.handleIllegalArgument(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Bad argument");
    }

    @Test
    void shouldHandleIllegalState() {
        IllegalStateException ex = new IllegalStateException("Conflict state");

        ProblemDetail problem = handler.handleIllegalState(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Conflict state");
    }

    @Test
    void shouldHandleGeneralException() {
        RuntimeException ex = new RuntimeException("Unexpected error");

        ProblemDetail problem = handler.handleGeneralException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
