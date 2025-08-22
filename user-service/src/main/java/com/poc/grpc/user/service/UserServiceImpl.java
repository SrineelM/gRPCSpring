package com.poc.grpc.user.service;

import com.poc.grpc.common.exception.UserAlreadyExistsException;
import com.poc.grpc.user.*;
import com.poc.grpc.user.entity.User;
import com.poc.grpc.user.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * gRPC User Service Implementation
 *
 * <p>This service handles user management operations in the microservices architecture: 1. User
 * Registration: Creates new user accounts with profiles 2. Authentication: Validates credentials
 * and generates JWT tokens 3. Profile Management: Updates and retrieves user profiles 4. User
 * Validation: Verifies user status for other services
 *
 * <p>Features: - Transactional processing - Redis caching for performance - JWT authentication -
 * Password encryption - Circuit breaker pattern
 *
 * <p>Security: - BCrypt password hashing - Account locking after failed attempts - Email
 * verification tracking - JWT token management
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  @Transactional
  @CircuitBreaker(name = "userService", fallbackMethod = "createUserFallback")
  public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
    String username = request.getUsername();
    log.info("Starting user creation process for username: {}", username);
    log.debug(
        "Create user request details - Email: {}, FirstName: {}",
        request.getEmail(),
        request.getFirstName());

    try {
      // Validate request
      validateCreateRequest(request);
      log.debug("Request validation passed for username: {}", username);

      // Check for existing user
      if (userRepository.existsByUsernameOrEmail(username, request.getEmail())) {
        log.warn(
            "User creation failed - Username or email already exists: {}, {}",
            username,
            request.getEmail());
        throw new UserAlreadyExistsException("User with username or email already exists");
      }

      // Create user entity
      User user = buildUserFromRequest(request);
      log.debug("User entity built successfully for username: {}", username);

      // Save user
      User savedUser = userRepository.save(user);
      log.info(
          "User created successfully - ID: {}, Username: {}",
          savedUser.getId(),
          savedUser.getUsername());

      // Cache user validation status
      cacheUserValidationStatus(savedUser);
      log.debug("User validation status cached for ID: {}", savedUser.getId());

      // Build and send response
      UserResponse response = buildUserResponse(savedUser);
      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("User creation failed for username: {} - Error: {}", username, e.getMessage(), e);
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Failed to create user: " + e.getMessage())
              .asRuntimeException());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void validateUser(
      ValidateUserRequest request, StreamObserver<ValidateUserResponse> responseObserver) {
    String userId = request.getUserId();
    log.debug("Validating user: {}", userId);

    try {
      // Check cache first
      String cacheKey = "user:valid:" + userId;
      Boolean cachedResult = (Boolean) redisTemplate.opsForValue().get(cacheKey);

      if (cachedResult != null) {
        log.debug(
            "User validation result found in cache for ID: {} - Valid: {}", userId, cachedResult);
        sendValidationResponse(responseObserver, cachedResult);
        return;
      }

      // Cache miss, check database
      boolean isValid =
          userRepository.findById(UUID.fromString(userId)).map(User::isValidForOrder).orElse(false);

      // Cache the result
      redisTemplate.opsForValue().set(cacheKey, isValid, Duration.ofMinutes(30));
      log.debug("User validation result cached for ID: {} - Valid: {}", userId, isValid);

      sendValidationResponse(responseObserver, isValid);

    } catch (Exception e) {
      log.error("User validation failed for ID: {} - Error: {}", userId, e.getMessage(), e);
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Failed to validate user: " + e.getMessage())
              .asRuntimeException());
    }
  }

  // Helper Methods

  private void validateCreateRequest(CreateUserRequest request) {
    log.debug("Validating create user request");

    if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
      throw new IllegalArgumentException("Username is required");
    }
    if (request.getEmail() == null || !request.getEmail().contains("@")) {
      throw new IllegalArgumentException("Valid email is required");
    }
    if (request.getPassword() == null || request.getPassword().length() < 8) {
      throw new IllegalArgumentException("Password must be at least 8 characters");
    }
  }

  private User buildUserFromRequest(CreateUserRequest request) {
    log.debug("Building user entity from request");

    return User.builder()
        .username(request.getUsername())
        .email(request.getEmail())
        .password(passwordEncoder.encode(request.getPassword()))
        .firstName(request.getFirstName())
        .lastName(request.getLastName())
        .isActive(true)
        .isEmailVerified(false)
        .build();
  }

  private UserResponse buildUserResponse(User user) {
    log.debug("Building user response for ID: {}", user.getId());

    return UserResponse.newBuilder()
        .setUserId(user.getId().toString())
        .setUsername(user.getUsername())
        .setEmail(user.getEmail())
        .setFirstName(user.getFirstName())
        .setLastName(user.getLastName())
        .setIsActive(user.isActive())
        .setIsEmailVerified(user.isEmailVerified())
        .build();
  }

  private void cacheUserValidationStatus(User user) {
    try {
      String cacheKey = "user:valid:" + user.getId();
      redisTemplate.opsForValue().set(cacheKey, user.isValidForOrder(), Duration.ofHours(24));
      log.debug("User validation status cached with key: {}", cacheKey);
    } catch (Exception e) {
      log.warn(
          "Failed to cache user validation status - ID: {} - Error: {}",
          user.getId(),
          e.getMessage());
      // Don't throw exception as caching failure shouldn't affect the main flow
    }
  }

  private void sendValidationResponse(
      StreamObserver<ValidateUserResponse> responseObserver, boolean isValid) {
    ValidateUserResponse response = ValidateUserResponse.newBuilder().setValid(isValid).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  // Fallback Methods

  private void createUserFallback(
      CreateUserRequest request, StreamObserver<UserResponse> responseObserver, Exception e) {
    log.error(
        "Circuit breaker triggered for user creation - Username: {} - Error: {}",
        request.getUsername(),
        e.getMessage(),
        e);
    responseObserver.onError(
        Status.UNAVAILABLE
            .withDescription("Service temporarily unavailable, please try again later")
            .asRuntimeException());
  }
}
