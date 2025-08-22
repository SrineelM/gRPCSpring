package com.poc.grpc.order.exception;

import io.grpc.Status;
import lombok.Getter;

/**
 * Base exception class for Order Service exceptions.
 *
 * <p>This exception is used to signal specific error conditions within the order service that can
 * be translated into appropriate gRPC status codes by a central exception handler. It allows for
 * consistent error handling across services with detailed error codes for better client handling.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * if (order == null) {
 *   throw OrderServiceException.orderNotFound(orderId);
 * }
 * }</pre>
 */
@Getter
public class OrderServiceException extends RuntimeException {

  private final Status status;
  private final ErrorCode errorCode;

  /**
   * Creates a new OrderServiceException with the specified message, cause, gRPC status, and error
   * code.
   *
   * @param message The error message
   * @param cause The cause of this exception
   * @param status The gRPC status to return to clients
   * @param errorCode The specific error code for this exception
   */
  public OrderServiceException(
      String message, Throwable cause, Status status, ErrorCode errorCode) {
    super(message, cause);
    this.status = status;
    this.errorCode = errorCode;
  }

  /**
   * Creates a new OrderServiceException with the specified message, gRPC status, and error code.
   *
   * @param message The error message
   * @param status The gRPC status to return to clients
   * @param errorCode The specific error code for this exception
   */
  public OrderServiceException(String message, Status status, ErrorCode errorCode) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  /**
   * Enumeration of specific error codes for the Order Service. These provide more detailed
   * information about the error than just the gRPC status.
   */
  public enum ErrorCode {
    // General errors
    UNKNOWN_ERROR(1000),
    INTERNAL_SERVER_ERROR(1001),
    INVALID_REQUEST(1002),

    // Order-specific errors
    ORDER_NOT_FOUND(2000),
    ORDER_CREATION_FAILED(2001),
    ORDER_UPDATE_FAILED(2002),
    ORDER_DELETION_FAILED(2003),
    INVALID_ORDER_STATUS(2004),
    ORDER_VALIDATION_FAILED(2005),

    // Item-specific errors
    ITEM_NOT_FOUND(3000),
    ITEM_CREATION_FAILED(3001),
    ITEM_UPDATE_FAILED(3002),
    ITEM_DELETION_FAILED(3003),

    // User-specific errors
    USER_NOT_FOUND(4000),
    USER_AUTHENTICATION_FAILED(4001),
    USER_AUTHORIZATION_FAILED(4002),

    // Saga-specific errors
    SAGA_EXECUTION_FAILED(5000),
    SAGA_COMPENSATION_FAILED(5001),

    // Database errors
    DATABASE_ERROR(6000),
    OPTIMISTIC_LOCK_ERROR(6001),
    UNIQUE_CONSTRAINT_VIOLATION(6002);

    private final int code;

    ErrorCode(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }
  }

  /**
   * Creates a NOT_FOUND exception for when an order with the specified ID doesn't exist.
   *
   * @param orderId The ID of the order that wasn't found
   * @return A new OrderNotFoundException
   */
  public static OrderNotFoundException orderNotFound(String orderId) {
    return new OrderNotFoundException("Order not found with ID: " + orderId);
  }

  /**
   * Creates an INVALID_ARGUMENT exception for when order validation fails.
   *
   * @param message The validation error message
   * @return A new OrderValidationException
   */
  public static OrderValidationException validationFailed(String message) {
    return new OrderValidationException(message);
  }

  /**
   * Creates an ALREADY_EXISTS exception for when trying to create a duplicate order.
   *
   * @param orderId The ID of the duplicate order
   * @return A new OrderAlreadyExistsException
   */
  public static OrderAlreadyExistsException orderAlreadyExists(String orderId) {
    return new OrderAlreadyExistsException("Order already exists with ID: " + orderId);
  }

  /**
   * Creates a FAILED_PRECONDITION exception for when trying to update an order with an invalid
   * status transition.
   *
   * @param currentStatus The current status of the order
   * @param newStatus The invalid new status
   * @return A new InvalidOrderStatusException
   */
  public static InvalidOrderStatusException invalidStatusTransition(
      String currentStatus, String newStatus) {
    return new InvalidOrderStatusException(
        String.format("Invalid order status transition from %s to %s", currentStatus, newStatus));
  }

  /**
   * Creates an INTERNAL exception for database errors.
   *
   * @param message The error message
   * @param cause The cause of the error
   * @return A new DatabaseException
   */
  public static DatabaseException databaseError(String message, Throwable cause) {
    return new DatabaseException(message, cause);
  }

  /**
   * Creates an ABORTED exception for optimistic locking errors.
   *
   * @param orderId The ID of the order with the concurrency conflict
   * @return A new OptimisticLockException
   */
  public static OptimisticLockException optimisticLockError(String orderId) {
    return new OptimisticLockException("Concurrent modification detected for order: " + orderId);
  }

  /**
   * Creates a PERMISSION_DENIED exception for authorization errors.
   *
   * @param userId The ID of the user lacking permission
   * @param orderId The ID of the order being accessed
   * @return A new AuthorizationException
   */
  public static AuthorizationException authorizationFailed(String userId, String orderId) {
    return new AuthorizationException(
        String.format("User %s is not authorized to access order %s", userId, orderId));
  }

  /**
   * Creates a UNAVAILABLE exception for when a remote service is unavailable.
   *
   * @param serviceName The name of the unavailable service
   * @return A new ServiceUnavailableException
   */
  public static ServiceUnavailableException serviceUnavailable(String serviceName) {
    return new ServiceUnavailableException("Service unavailable: " + serviceName);
  }

  /**
   * Creates a DEADLINE_EXCEEDED exception for when a request timeout occurs.
   *
   * @param operationName The name of the operation that timed out
   * @return A new RequestTimeoutException
   */
  public static RequestTimeoutException requestTimeout(String operationName) {
    return new RequestTimeoutException("Request timeout for operation: " + operationName);
  }
}

/** Exception thrown when an order is not found. */
class OrderNotFoundException extends OrderServiceException {
  public OrderNotFoundException(String message) {
    super(message, Status.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND);
  }
}

/** Exception thrown when order validation fails. */
class OrderValidationException extends OrderServiceException {
  public OrderValidationException(String message) {
    super(message, Status.INVALID_ARGUMENT, ErrorCode.ORDER_VALIDATION_FAILED);
  }
}

/** Exception thrown when attempting to create a duplicate order. */
class OrderAlreadyExistsException extends OrderServiceException {
  public OrderAlreadyExistsException(String message) {
    super(message, Status.ALREADY_EXISTS, ErrorCode.ORDER_CREATION_FAILED);
  }
}

/** Exception thrown when an invalid order status transition is attempted. */
class InvalidOrderStatusException extends OrderServiceException {
  public InvalidOrderStatusException(String message) {
    super(message, Status.FAILED_PRECONDITION, ErrorCode.INVALID_ORDER_STATUS);
  }
}

/** Exception thrown when a database error occurs. */
class DatabaseException extends OrderServiceException {
  public DatabaseException(String message, Throwable cause) {
    super(message, cause, Status.INTERNAL, ErrorCode.DATABASE_ERROR);
  }
}

/** Exception thrown when an optimistic locking error occurs. */
class OptimisticLockException extends OrderServiceException {
  public OptimisticLockException(String message) {
    super(message, Status.ABORTED, ErrorCode.OPTIMISTIC_LOCK_ERROR);
  }
}

/** Exception thrown when authorization fails. */
class AuthorizationException extends OrderServiceException {
  public AuthorizationException(String message) {
    super(message, Status.PERMISSION_DENIED, ErrorCode.USER_AUTHORIZATION_FAILED);
  }
}

/** Exception thrown when a remote service is unavailable. */
class ServiceUnavailableException extends OrderServiceException {
  public ServiceUnavailableException(String message) {
    super(message, Status.UNAVAILABLE, ErrorCode.SAGA_EXECUTION_FAILED);
  }
}

/** Exception thrown when a request timeout occurs. */
class RequestTimeoutException extends OrderServiceException {
  public RequestTimeoutException(String message) {
    super(message, Status.DEADLINE_EXCEEDED, ErrorCode.SAGA_EXECUTION_FAILED);
  }
}
