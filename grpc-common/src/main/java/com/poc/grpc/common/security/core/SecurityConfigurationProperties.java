package com.poc.grpc.common.security.core;

import com.poc.grpc.common.security.enums.SecurityLevel;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Centralized security configuration properties for JWT and gRPC security. This class consolidates
 * all security-related configuration to provide a single source of truth and eliminate
 * scattered @Value annotations.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityConfigurationProperties {
  /**
   * JWT configuration properties
   *
   * <p>Example in application.yml: app.security.jwt.secret: "..." app.security.jwt.issuer: "..."
   * app.security.jwt.audience: "..." app.security.jwt.expirationMs: 86400000
   * app.security.jwt.rolesClaim: "roles"
   */
  private final Jwt jwt;

  /**
   * gRPC security configuration
   *
   * <p>Example in application.yml: app.security.grpc.serverSecurityLevel: FULL_SPRING_SECURITY
   * app.security.grpc.clientSecurityLevel: TOKEN_PROPAGATION
   * app.security.grpc.requireAuthForAllMethods: true app.security.grpc.excludedMethods: []
   */
  private final Grpc grpc;

  /**
   * HTTP security configuration
   *
   * <p>Example in application.yml: app.security.http.enabled: true
   * app.security.http.excludedPatterns: ["/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"]
   */
  private final Http http;

  public SecurityConfigurationProperties(Jwt jwt, Grpc grpc, Http http) {
    this.jwt = jwt;
    this.grpc = grpc;
    this.http = http;
  }

  @Data
  public static class Jwt {
    /** Whether JWT security is enabled */
    private boolean enabled = true;

    /** JWT signing secret (Base64 encoded) */
    @NotBlank(message = "JWT secret cannot be blank")
    private String secret;

    /** JWT issuer claim */
    @NotBlank(message = "JWT issuer cannot be blank")
    private String issuer;

    /** JWT audience claim */
    @NotBlank(message = "JWT audience cannot be blank")
    private String audience;

    /** JWT expiration time in milliseconds */
    @Positive(message = "JWT expiration must be positive")
    private int expirationMs = 86400000; // 24 hours

    /** JWT roles claim name */
    private String rolesClaim = "roles";
  }

  @Data
  public static class Grpc {
    /** Security level for gRPC services */
    @NotNull private SecurityLevel serverSecurityLevel = SecurityLevel.FULL_SPRING_SECURITY;

    /** Security level for gRPC clients */
    @NotNull private SecurityLevel clientSecurityLevel = SecurityLevel.TOKEN_PROPAGATION;

    /** Whether to require authentication for all gRPC methods */
    private boolean requireAuthForAllMethods = true;

    /** Methods that are excluded from authentication (format: service/method) */
    private String[] excludedMethods = {};
  }

  @Data
  public static class Http {
    /** Whether HTTP JWT filter is enabled */
    private boolean enabled = true;

    /** URL patterns to exclude from JWT authentication */
    private String[] excludedPatterns = {"/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"};
  }
}
