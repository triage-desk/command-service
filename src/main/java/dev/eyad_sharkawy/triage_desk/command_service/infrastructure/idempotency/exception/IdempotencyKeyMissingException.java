package dev.eyad_sharkawy.triage_desk.command_service.infrastructure.idempotency.exception;

public class IdempotencyKeyMissingException extends RuntimeException {
    public IdempotencyKeyMissingException(String message) {
        super(message);
    }
}
