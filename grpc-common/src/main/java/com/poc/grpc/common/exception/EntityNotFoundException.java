package com.poc.grpc.common.exception;

/**
 * Entity Not Found Exception
 *
 * <p>This exception is thrown when a requested entity cannot be found in the database. It provides
 * a standardized way to handle missing entities across the microservices.
 *
 * <p>Usage: - Thrown by repositories when entities are not found - Caught by
 * GlobalGrpcExceptionHandler - Mapped to gRPC NOT_FOUND status
 *
 * <p>Example: ```java User user = userRepository.findById(id) .orElseThrow(() -> new
 * EntityNotFoundException("User not found: " + id)); ```
 */
public class EntityNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new EntityNotFoundException with a message.
   *
   * @param message The error message
   */
  public EntityNotFoundException(String message) {
    super(message);
  }

  /**
   * Creates a new EntityNotFoundException with a message and cause.
   *
   * @param message The error message
   * @param cause The cause of the exception
   */
  public EntityNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new EntityNotFoundException for a specific entity type and ID.
   *
   * @param entityType The type of entity that was not found
   * @param id The ID that was searched for
   * @return A new EntityNotFoundException with a formatted message
   */
  public static EntityNotFoundException forEntity(String entityType, Object id) {
    return new EntityNotFoundException(String.format("%s not found with ID: %s", entityType, id));
  }
}
