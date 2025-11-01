package com.poc.grpc.common.exception;

import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
<<<<<<< HEAD
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Global gRPC Exception Handler
 *
 * <p>This class provides centralized exception handling for gRPC services. It converts Java
 * exceptions into appropriate gRPC Status codes and ensures consistent error handling across
 * services.
 *
 * <p>Features: 1. Exception to Status mapping 2. Detailed error messages 3. Security exception
 * handling 4. Audit logging
 *
 * <p>Status Code Mapping: - EntityNotFoundException → NOT_FOUND - AuthenticationException →
 * UNAUTHENTICATED - AccessDeniedException → PERMISSION_DENIED - IllegalArgumentException →
 * INVALID_ARGUMENT - Other exceptions → INTERNAL
 *
 * <p>Usage: - Automatically applied to all gRPC services - Logs exceptions with appropriate levels
 * - Provides meaningful error messages
 */
@Slf4j
<<<<<<< HEAD
@GrpcAdvice
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
public class GlobalGrpcExceptionHandler {

  /**
   * Handles entity not found exceptions. Maps to gRPC NOT_FOUND status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(EntityNotFoundException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleEntityNotFound(EntityNotFoundException e) {
    log.warn("Entity not found: {}", e.getMessage());
    return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
  }

  /**
   * Handles authentication exceptions. Maps to gRPC UNAUTHENTICATED status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(AuthenticationException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleAuthentication(AuthenticationException e) {
    log.warn("Authentication failed: {}", e.getMessage());
    return Status.UNAUTHENTICATED.withDescription("Authentication failed").withCause(e);
  }

  /**
   * Handles access denied exceptions. Maps to gRPC PERMISSION_DENIED status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(AccessDeniedException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleAccessDenied(AccessDeniedException e) {
    log.warn("Access denied: {}", e.getMessage());
    return Status.PERMISSION_DENIED.withDescription("Access denied").withCause(e);
  }

  /**
   * Handles JWT generation exceptions. Maps to gRPC INTERNAL status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(JwtGenerationException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleJwtGeneration(JwtGenerationException e) {
    log.error("JWT generation failed: {}", e.getMessage(), e);
    return Status.INTERNAL.withDescription("Failed to generate authentication token").withCause(e);
  }

  /**
   * Handles JWT validation exceptions. Maps to gRPC UNAUTHENTICATED status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(JwtValidationException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleJwtValidation(JwtValidationException e) {
    log.warn("JWT validation failed: {}", e.getMessage());
    return Status.UNAUTHENTICATED.withDescription("Invalid authentication token").withCause(e);
  }

  /**
   * Handles illegal argument exceptions. Maps to gRPC INVALID_ARGUMENT status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(IllegalArgumentException.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleIllegalArgument(IllegalArgumentException e) {
    log.warn("Invalid argument: {}", e.getMessage());
    return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e);
  }

  /**
   * Handles all other uncaught exceptions. Maps to gRPC INTERNAL status.
   *
   * @param e The exception to handle
   * @return gRPC Status with error details
   */
<<<<<<< HEAD
  @GrpcExceptionHandler(Exception.class)
=======
>>>>>>> d6807baff8512f81dea1b7d4742df3013d4d23d4
  public Status handleGeneric(Exception e) {
    log.error("Unexpected error in gRPC service: {}", e.getMessage(), e);
    return Status.INTERNAL.withDescription("Internal server error").withCause(e);
  }
}
