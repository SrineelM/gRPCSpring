package com.poc.grpc.common.exception;

/**
 * User Already Exists Exception
 *
 * <p>This exception is thrown during user registration when a user with the same username or email
 * already exists in the system. It helps maintain data integrity by preventing duplicate users.
 *
 * <p>Usage: - Thrown during user creation - Caught by GlobalGrpcExceptionHandler - Mapped to gRPC
 * ALREADY_EXISTS status
 *
 * <p>Example: ```java if (userRepository.existsByUsername(username)) { throw new
 * UserAlreadyExistsException("Username already taken: " + username); } ```
 */
public class UserAlreadyExistsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new UserAlreadyExistsException with a message.
   *
   * @param message The error message
   */
  public UserAlreadyExistsException(String message) {
    super(message);
  }

  /**
   * Creates a new UserAlreadyExistsException with a message and cause.
   *
   * @param message The error message
   * @param cause The cause of the exception
   */
  public UserAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new UserAlreadyExistsException for a specific username.
   *
   * @param username The username that already exists
   * @return A new UserAlreadyExistsException with a formatted message
   */
  public static UserAlreadyExistsException forUsername(String username) {
    return new UserAlreadyExistsException(
        String.format("User already exists with username: %s", username));
  }

  /**
   * Creates a new UserAlreadyExistsException for a specific email.
   *
   * @param email The email that already exists
   * @return A new UserAlreadyExistsException with a formatted message
   */
  public static UserAlreadyExistsException forEmail(String email) {
    return new UserAlreadyExistsException(
        String.format("User already exists with email: %s", email));
  }
}
