package com.poc.grpc.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Order service specific security configuration. Focuses only on order-service-specific security
 * needs while leveraging auto-configuration from the common module.
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
@Slf4j
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@Configuration
public class OrderSecurityConfig {
  // Security configuration is handled by the common module auto-configuration
}
