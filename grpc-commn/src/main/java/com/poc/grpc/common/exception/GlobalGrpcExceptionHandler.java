package com.poc.grpc.order.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.timelimiter.exception.TimeoutException;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationException;

/**
 * Global gRPC exception handler to catch exceptions from gRPC service implementations
 * and convert them into appropriate gRPC status responses. This class uses the
 * {@code @GrpcAdvice} annotation from the 'grpc-spring-boot-starter' library,
 * making it a centralized place for error handling logic, similar to {@code @ControllerAdvice}
 * in Spring MVC.
 * <p>
 * Note: The package is 'com.poc.grpc.order.exception' while the file path suggests 'user'.
 * This is a minor inconsistency that could be aligned for better project structure.
 */
@Slf4j
@GrpcAdvice
public class GlobalGrpcExceptionHandler {

    /**
     * Metadata key for transmitting a unique correlation ID for each error.
     * This helps in tracing and debugging requests between client and server.
     */
    private static final Metadata.Key<String> CORRELATION_ID_KEY =
            Metadata.Key.of("correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Handles {@link EntityNotFoundException}, which is typically thrown when a requested
     * resource cannot be found in the database.
     *
     * @param e The caught {@code EntityNotFoundException}.
     * @return A {@link StatusRuntimeException} with a {@link Status#NOT_FOUND} code.
     */
    @GrpcExceptionHandler(EntityNotFoundException.class)
    public StatusRuntimeException handleNotFound(EntityNotFoundException e) {
        return createErrorResponse(e, Status.NOT_FOUND, "Entity not found", e.getMessage(), true);
    }

    /**
     * Handles {@link IllegalArgumentException}, indicating that a method has been passed an
     * illegal or inappropriate argument.
     *
     * @param e The caught {@code IllegalArgumentException}.
     * @return A {@link StatusRuntimeException} with an {@link Status#INVALID_ARGUMENT} code.
     */
    @GrpcExceptionHandler(IllegalArgumentException.class)
    public StatusRuntimeException handleInvalidArgument(IllegalArgumentException e) {
        return createErrorResponse(e, Status.INVALID_ARGUMENT, "Invalid argument", e.getMessage(), true);
    }

    /**
     * Handles {@link CallNotPermittedException} from Resilience4j, which indicates that
     * the CircuitBreaker is open and not permitting calls.
     *
     * @param e The caught {@code CallNotPermittedException}.
     * @return A {@link StatusRuntimeException} with an {@link Status#UNAVAILABLE} code.
     */
    @GrpcExceptionHandler(CallNotPermittedException.class)
    public StatusRuntimeException handleCircuitBreakerOpen(CallNotPermittedException e) {
        return createErrorResponse(e, Status.UNAVAILABLE, "Circuit breaker open", "Service temporarily unavailable - circuit breaker open", true);
    }

    /**
     * Handles {@link TimeoutException} from Resilience4j's TimeLimiter, indicating that a
     * call has exceeded its configured time limit.
     *
     * @param e The caught {@code TimeoutException}.
     * @return A {@link StatusRuntimeException} with a {@link Status#DEADLINE_EXCEEDED} code.
     */
    @GrpcExceptionHandler(TimeoutException.class)
    public StatusRuntimeException handleTimeout(TimeoutException e) {
        return createErrorResponse(e, Status.DEADLINE_EXCEEDED, "Request timeout", "Request timeout exceeded", true);
    }

    /**
     * Handles {@link OptimisticLockException}, which occurs during a concurrent update
     * conflict on a versioned entity.
     *
     * @param e The caught {@code OptimisticLockException}.
     * @return A {@link StatusRuntimeException} with an {@link Status#ABORTED} code,
     *         suggesting the client should retry the transaction.
     */
    @GrpcExceptionHandler(OptimisticLockException.class)
    public StatusRuntimeException handleOptimisticLock(OptimisticLockException e) {
        return createErrorResponse(e, Status.ABORTED, "Optimistic lock exception", "Resource was modified by another transaction. Please retry.", true);
    }

    /**
     * Handles {@link DataIntegrityViolationException}, indicating a violation of a database
     * constraint (e.g., unique key violation).
     *
     * @param e The caught {@code DataIntegrityViolationException}.
     * @return A {@link StatusRuntimeException} with a {@link Status#FAILED_PRECONDITION} code.
     */
    @GrpcExceptionHandler(DataIntegrityViolationException.class)
    public StatusRuntimeException handleDataIntegrity(DataIntegrityViolationException e) {
        return createErrorResponse(e, Status.FAILED_PRECONDITION, "Data integrity violation", "Data integrity constraint violated", true);
    }

    /**
     * Handles {@link AuthenticationException}, indicating a failure during the
     * authentication process. The cause is not propagated to the client to avoid leaking details.
     *
     * @param e The caught {@code AuthenticationException}.
     * @return A {@link StatusRuntimeException} with an {@link Status#UNAUTHENTICATED} code.
     */
    @GrpcExceptionHandler(AuthenticationException.class)
    public StatusRuntimeException handleAuthentication(AuthenticationException e) {
        return createErrorResponse(e, Status.UNAUTHENTICATED, "Authentication failed", "Authentication failed", false);
    }

    /**
     * Handles {@link AccessDeniedException}, indicating that a user is attempting to access
     * a resource they do not have permission for. The cause is not propagated to the client.
     *
     * @param e The caught {@code AccessDeniedException}.
     * @return A {@link StatusRuntimeException} with a {@link Status#PERMISSION_DENIED} code.
     */
    @GrpcExceptionHandler(AccessDeniedException.class)
    public StatusRuntimeException handleAccessDenied(AccessDeniedException e) {
        return createErrorResponse(e, Status.PERMISSION_DENIED, "Access denied", "Access denied", false);
    }

    /**
     * Handles {@link CompletionException}, which wraps exceptions thrown from asynchronous
     * computations. This handler unwraps the underlying cause to provide a more specific error.
     *
     * @param e The caught {@code CompletionException}.
     * @return A {@link StatusRuntimeException} derived from the cause of the exception.
     */
    @GrpcExceptionHandler(CompletionException.class)
    public StatusRuntimeException handleCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        // If the cause is already a gRPC exception, just re-throw it.
        if (cause instanceof StatusRuntimeException) {
            return (StatusRuntimeException) cause;
        }
        // Otherwise, wrap it in a generic INTERNAL error.
        return createErrorResponse(cause, Status.INTERNAL, "Async operation failed", "Async operation failed", true);
    }

    /**
     * A catch-all handler for any other unhandled {@link Exception}. This is a safety net
     * to prevent the server from sending an unhelpful UNKNOWN status.
     *
     * @param e The caught {@code Exception}.
     * @return A {@link StatusRuntimeException} with an {@link Status#INTERNAL} code.
     */
    @GrpcExceptionHandler(Exception.class)
    public StatusRuntimeException handleAll(Exception e) {
        return createErrorResponse(e, Status.INTERNAL, "Unexpected error", "Internal server error occurred", true);
    }

    /**
     * Private helper to create a standardized gRPC error response. It generates a correlation ID,
     * logs the error, and builds the {@link StatusRuntimeException} with metadata.
     *
     * @param e              The exception that was caught.
     * @param status         The gRPC status to return.
     * @param logMessage     A short message for logging purposes.
     * @param description    The description to be sent to the client.
     * @param includeCause   Whether to include the exception's cause in the gRPC status.
     * @return A configured {@link StatusRuntimeException}.
     */
    private StatusRuntimeException createErrorResponse(Throwable e, Status status, String logMessage, String description, boolean includeCause) {
        String correlationId = UUID.randomUUID().toString();
        log.error("{} - Correlation ID: {}", logMessage, correlationId, e);

        Metadata metadata = new Metadata();
        metadata.put(CORRELATION_ID_KEY, correlationId);

        Status statusToSend = status.withDescription(description);
        if (includeCause) {
            statusToSend = statusToSend.withCause(e);
        }

        return statusToSend.asRuntimeException(metadata);
    }
}