package com.poc.grpc.common.security.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Defines different levels of security that can be applied to gRPC services and clients. This
 * allows for flexible security configuration based on service requirements.
 */
public enum SecurityLevel {
  /** No security - all requests are allowed */
  NONE,

  /** Basic JWT validation only - validates token signature and expiration */
  BASIC_VALIDATION,

  /**
   * Full Spring Security integration - validates token, loads user details, populates
   * SecurityContextHolder, enables @PreAuthorize annotations
   */
  FULL_SPRING_SECURITY,

  /** Token propagation for clients - automatically adds JWT to outgoing requests */
  TOKEN_PROPAGATION,

  /** Client-side validation - validates tokens before sending requests */
  CLIENT_VALIDATION;

  // Sets for common security capabilities
  private static final Set<SecurityLevel> JWT_VALIDATING_LEVELS =
      EnumSet.of(BASIC_VALIDATION, FULL_SPRING_SECURITY, CLIENT_VALIDATION);

  private static final Set<SecurityLevel> SPRING_SECURITY_LEVELS = EnumSet.of(FULL_SPRING_SECURITY);

  private static final Set<SecurityLevel> CLIENT_TOKEN_LEVELS =
      EnumSet.of(TOKEN_PROPAGATION, CLIENT_VALIDATION);

  /**
   * Checks if this security level performs JWT validation.
   *
   * @return true if this level validates JWT tokens
   */
  public boolean doesJwtValidation() {
    return JWT_VALIDATING_LEVELS.contains(this);
  }

  /**
   * Checks if this security level integrates with Spring Security.
   *
   * @return true if this level populates SecurityContextHolder
   */
  public boolean usesSpringSecurityContext() {
    return SPRING_SECURITY_LEVELS.contains(this);
  }

  /**
   * Checks if this security level is suitable for client-side use.
   *
   * @return true if this level is designed for clients
   */
  public boolean isClientSideLevel() {
    return CLIENT_TOKEN_LEVELS.contains(this);
  }

  /**
   * Checks if this security level requires a JWT token.
   *
   * @return true if this level needs a JWT token
   */
  public boolean requiresJwtToken() {
    return this != NONE;
  }
}
