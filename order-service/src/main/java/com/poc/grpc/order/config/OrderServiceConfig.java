package com.poc.grpc.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

/**
 * Order Service Configuration
 *
 * <p>This configuration class sets up beans and components specific to the order service. It
 * configures caching, resilience patterns, and auditing functionality.
 *
 * <p>Features: 1. Redis caching setup 2. Resilience4j circuit breaker 3. Audit configuration 4.
 * Distributed tracing
 *
 * <p>Dependencies: - Redis for caching - Resilience4j for fault tolerance - JPA Auditing for
 * tracking
 */
@Slf4j
@Configuration
@EnableCaching
public class OrderServiceConfig {

  /**
   * Configures circuit breaker for order service operations. Provides fault tolerance for critical
   * operations.
   *
   * @return The circuit breaker registry
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
   * Configures auditing for JPA entities. Tracks creation and modification timestamps.
   *
   * @return The auditor provider
   */
  @Bean
  public AuditorAware<String> auditorProvider() {
    log.debug("Configuring JPA auditor provider");
    return () -> Optional.of("system");
  }
}

/**
 * Centralized configuration for Order Service.
 *
 * <p>Uses constructor injection and extracts constants for maintainability.
 */
final class OrderConstants {
  private OrderConstants() {}

  public static final String ORDER_TOPIC = "orders";
  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final String ORDER_ID_PREFIX = "ORD-";
  public static final long ORDER_EXPIRATION_DAYS = 30;
}
