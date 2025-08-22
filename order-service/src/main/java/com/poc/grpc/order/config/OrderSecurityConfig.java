package com.poc.grpc.order.config;

import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

/**
 * Order service specific security configuration. Focuses only on order-service-specific security
 * needs while leveraging auto-configuration from the common module.
 */
@Slf4j
@EnableGlobalMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@Configuration
/**
 * Security configuration for Order Service.
 *
 * <p>This configuration sets up JWT-based authentication for gRPC endpoints.
 *
 * <p>Security implications:
 *
 * <ul>
 *   <li>All order operations require authenticated users
 *   <li>Order creation requires ROLE_USER or higher
 *   <li>Order deletion requires ROLE_ADMIN
 *   <li>Users can only view their own orders unless they have ROLE_ADMIN
 *   <li>JWT tokens are validated against expiration and signature
 * </ul>
 */
public class OrderSecurityConfig {

  /** Order service specific security properties */
  @Bean
  public SecurityConfigurationProperties orderSecurityProperties(
      SecurityConfigurationProperties properties) {
    // Order service validates incoming tokens with basic validation for performance
    properties.getGrpc().setServerSecurityLevel(SecurityLevel.BASIC_VALIDATION);
    // Order service validates tokens before making upstream calls
    properties.getGrpc().setClientSecurityLevel(SecurityLevel.CLIENT_VALIDATION);
    // Exclude health check methods from authentication
    properties
        .getGrpc()
        .setExcludedMethods(
            new String[] {
              "grpc.health.v1.Health/Check", "com.poc.grpc.order.OrderService/HealthCheck"
            });
    log.info("Configured order service security properties");
    return properties;
  }
}
