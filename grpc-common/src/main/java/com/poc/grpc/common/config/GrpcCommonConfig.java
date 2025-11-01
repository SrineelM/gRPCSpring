package com.poc.grpc.common.config;

import com.poc.grpc.common.interceptor.CorrelationIdInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Configuration class for registering global gRPC interceptors in the common module.
 *
 * <p>This configuration ensures that cross-cutting concerns are applied to all gRPC services:
 *
 * <ul>
 *   <li>Correlation ID tracking for distributed tracing
 *   <li>Request logging with MDC context
 *   <li>Performance monitoring
 * </ul>
 *
 * <p><strong>Interceptor Order:</strong>
 *
 * <ol>
 *   <li>CorrelationIdInterceptor (Order.HIGHEST_PRECEDENCE) - Adds correlation ID first
 *   <li>GrpcAuthInterceptor (default order) - Handles authentication
 *   <li>Service-specific interceptors (lowest order) - Business logic
 * </ol>
 *
 * <p>The correlation ID interceptor has the highest precedence to ensure that all subsequent
 * interceptors and service methods can access the correlation ID through MDC or gRPC Context.
 *
 * @author gRPC Spring Team
 * @since 1.0.0
 */
@Configuration
public class GrpcCommonConfig {

  /**
   * Registers the correlation ID interceptor globally for all gRPC services.
   *
   * <p>This interceptor is applied before authentication to ensure correlation IDs are available
   * for logging authentication events.
   *
   * @return the interceptor instance for global registration
   */
  @org.springframework.context.annotation.Bean
  @GrpcGlobalServerInterceptor
  @Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
  public CorrelationIdInterceptor correlationIdInterceptor() {
    return new CorrelationIdInterceptor();
  }
}
