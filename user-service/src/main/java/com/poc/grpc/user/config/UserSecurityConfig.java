package com.poc.grpc.user.config;

import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Security configuration for User Service.
 *
 * <p>This configuration sets up JWT-based authentication for gRPC endpoints with specific security
 * rules for user management.
 *
 * <p>Security implications:
 *
 * <ul>
 *   <li>User registration is permitted without authentication
 *   <li>User profile updates require authentication as the target user or ADMIN
 *   <li>User deletion requires ADMIN role
 *   <li>Password changes require either self-user or ADMIN role
 *   <li>Role changes require ADMIN role
 * </ul>
 */
@Slf4j
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@Configuration
public class UserSecurityConfig {

  /** Password encoder for user credentials */
  @Bean
  public PasswordEncoder passwordEncoder() {
    log.info("Configuring BCrypt password encoder with strength 12");
    return new BCryptPasswordEncoder(12);
  }

  /** User service specific security properties */
  @Bean
  public SecurityConfigurationProperties userSecurityProperties(
      SecurityConfigurationProperties properties) {
    // User service acts as identity provider - full security on server side
    properties.getGrpc().setServerSecurityLevel(SecurityLevel.FULL_SPRING_SECURITY);
    // User service propagates tokens to other services
    properties.getGrpc().setClientSecurityLevel(SecurityLevel.TOKEN_PROPAGATION);
    log.info("Configured user service security properties");
    return properties;
  }
}
