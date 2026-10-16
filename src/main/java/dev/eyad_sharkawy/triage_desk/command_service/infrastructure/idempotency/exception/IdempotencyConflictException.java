package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
