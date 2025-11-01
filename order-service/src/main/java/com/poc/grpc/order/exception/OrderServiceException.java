package com.poc.grpc.order.exception;

import io.grpc.Status;
import lombok.Getter;

/**
 * Custom exception for the Order Service.
 *
 * <p>This exception is used to signal specific error conditions within the order service that can
 * be translated into appropriate gRPC status codes by a central exception handler, likely located
 * in the grpc-common module. It allows for consistent error handling across services.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * if (order == null) {
 *   throw new OrderServiceException(Status.NOT_FOUND, "Order with ID " + orderId + " not found.");
 * }
 * }</pre>
 */
@Getter
public class OrderServiceException extends RuntimeException {

  private final Status status;

  /**
   * Constructs a new OrderServiceException with a specific gRPC status and a detail message.
   *
   * @param status The gRPC status to be returned to the client.
   * @param message The detail message.
   */
  public OrderServiceException(Status status, String message) {
    super(message);
    this.status = status;
  }

  /**
   * Constructs a new OrderServiceException with a specific gRPC status, a detail message, and a
   * cause.
   *
   * @param status The gRPC status to be returned to the client.
   * @param message The detail message.
   * @param cause The cause of the exception.
   */
  public OrderServiceException(Status status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }
}
