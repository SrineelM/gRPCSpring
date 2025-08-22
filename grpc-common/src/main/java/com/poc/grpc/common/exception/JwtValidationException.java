package com.poc.grpc.common.exception;

/**
 * Exception thrown when JWT token validation or parsing fails. This is a runtime exception as token
 * validation failures typically indicate security issues that should not be caught and handled
 * normally.
 */
public class JwtValidationException extends RuntimeException {
  public JwtValidationException(String message) {
    super(message);
  }

  public JwtValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}
