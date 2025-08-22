package com.poc.grpc.common.exception;

/**
 * Exception thrown when JWT token generation fails. This is a runtime exception as token generation
 * failures are typically unrecoverable.
 */
public class JwtGenerationException extends RuntimeException {
  public JwtGenerationException(String message) {
    super(message);
  }

  public JwtGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
