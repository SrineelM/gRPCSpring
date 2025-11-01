package com.poc.grpc.user.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configures essential beans for the User Service.
 *
 * <p>This class provides configuration for security, resilience, and auditing.
 */
@Slf4j
@Configuration
@EnableCaching
public class UserServiceConfig {

  /**
   * Creates a BCrypt password encoder bean for securely hashing user passwords. A strength of 12 is
   * used for a strong hashing algorithm.
   *
   * @return A {@link PasswordEncoder} instance.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    log.debug("Configuring BCrypt password encoder with strength 12");
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Creates a default {@link CircuitBreakerRegistry} for managing circuit breakers.
   *
   * <p>The configuration is set to: - 50% failure rate threshold. - 10-second wait duration in the
   * open state. - 5 permitted calls in the half-open state. - A sliding window of 10 calls, with a
   * minimum of 5 calls to calculate the failure rate.
   *
   * @return A configured {@link CircuitBreakerRegistry}.
   */
  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    log.debug("Configuring circuit breaker registry");

    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(5)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build();

    return CircuitBreakerRegistry.of(config);
  }

  /**
   * Provides a default auditor of "system" for JPA auditing. This is used to populate `createdBy`
   * and `lastModifiedBy` fields in audited entities.
   *
   * @return An {@link AuditorAware} instance.
   */
  @Bean
  public AuditorAware<String> auditorProvider() {
    log.debug("Configuring JPA auditor provider");
    return () -> Optional.of("system");
  }
}
