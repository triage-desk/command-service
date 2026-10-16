package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.web;

import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyConflictException;
import dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception.IdempotencyKeyMissingException;
import dev.eyad_sharkawy.triage_desk.common.dto.ValidationErrorDetail;
import dev.eyad_sharkawy.triage_desk.common.exceptions.ProblemDetailFactory;
import dev.eyad_sharkawy.triage_desk.common.exceptions.ResourceNotFoundException;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantAccessDeniedException;
import dev.eyad_sharkawy.triage_desk.common.exceptions.TenantNotFoundException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ProblemDetail handleTenantNotFound(TenantNotFoundException ex) {
        return ProblemDetailFactory.create(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ProblemDetail handleTenantAccessDenied(TenantAccessDeniedException ex) {
        return ProblemDetailFactory.create(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        return ProblemDetailFactory.create(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        List<ValidationErrorDetail> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fieldError ->
                                        new ValidationErrorDetail(
                                                fieldError.getField(),
                                                fieldError.getDefaultMessage() != null
                                                        ? fieldError.getDefaultMessage()
                                                        : "Invalid value",
                                                fieldError.getRejectedValue()))
                        .toList();

        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        log.error("Unhandled exception occurred", ex);
        return ProblemDetailFactory.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @ExceptionHandler(IdempotencyKeyMissingException.class)
    public ProblemDetail handleIdempotencyKeyMissing(IdempotencyKeyMissingException ex) {
        return ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex) {
        return ProblemDetailFactory.create(HttpStatus.CONFLICT, ex.getMessage());
    }
}
