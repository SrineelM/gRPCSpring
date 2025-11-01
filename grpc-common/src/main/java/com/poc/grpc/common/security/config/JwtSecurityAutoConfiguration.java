package com.poc.grpc.common.security.config;

import com.poc.grpc.common.security.JwtAuthenticationFilter;
import com.poc.grpc.common.security.JwtAuthenticator;
import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.interceptors.DefaultJwtClientInterceptor;
import com.poc.grpc.common.security.interceptors.DefaultJwtServerInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Auto-configuration class for JWT security components. This class automatically configures
 * security components based on application properties, eliminating the need for manual
 * configuration in each service.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SecurityConfigurationProperties.class)
@ComponentScan(basePackages = "com.poc.grpc.common.security")
@ConditionalOnProperty(
    name = "app.security.jwt.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JwtSecurityAutoConfiguration {

  /** Creates the default JWT server interceptor if no custom implementation is provided */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "app.security.grpc.server-security-level",
      havingValue = "FULL_SPRING_SECURITY",
      matchIfMissing = true)
  public DefaultJwtServerInterceptor defaultJwtServerInterceptor(
      JwtUtil jwtUtil,
      SecurityConfigurationProperties securityProperties,
      JwtAuthenticator jwtAuthenticator,
      MeterRegistry meterRegistry) {
    log.info(
        "Creating default JWT server interceptor with security level: {}",
        securityProperties.getGrpc().getServerSecurityLevel());
    return new DefaultJwtServerInterceptor(
        jwtUtil, securityProperties, jwtAuthenticator, meterRegistry);
  }

  /** Creates the default JWT client interceptor if no custom implementation is provided */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "app.security.grpc.client-security-level",
      havingValue = "TOKEN_PROPAGATION",
      matchIfMissing = true)
  public DefaultJwtClientInterceptor defaultJwtClientInterceptor(
      JwtUtil jwtUtil, SecurityConfigurationProperties securityProperties) {
    log.info(
        "Creating default JWT client interceptor with security level: {}",
        securityProperties.getGrpc().getClientSecurityLevel());
    return new DefaultJwtClientInterceptor(jwtUtil, securityProperties);
  }

  /** Creates JWT authenticator if UserDetailsService is available */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true")
  public JwtAuthenticator jwtAuthenticator(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
    log.info("Creating JWT authenticator with UserDetailsService integration");
    return new JwtAuthenticator(jwtUtil, userDetailsService);
  }

  /** Creates JWT authentication filter for HTTP endpoints */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = "app.security.http.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtUtil jwtUtil, SecurityConfigurationProperties securityProperties) {
    log.info("Creating JWT authentication filter for HTTP endpoints");
    return new JwtAuthenticationFilter(jwtUtil);
  }
}
