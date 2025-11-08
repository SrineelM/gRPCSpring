package com.poc.grpc.order.config;

import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.JwtValidationInterceptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JwtValidationInterceptor as a global gRPC client interceptor for the order service.
 * This ensures that all outgoing gRPC requests from the order service will have JWT validation
 * applied.
 *
 * <p>Beginners: This is the recommended way to add a gRPC client interceptor in Spring Boot with
 * grpc-spring-boot-starter.
 */
@Configuration // Marks this class as a Spring configuration
public class GrpcClientInterceptorConfig {

  @Bean // Registers the bean in the Spring context
  @GrpcGlobalClientInterceptor // Applies this interceptor to all gRPC clients
  public JwtValidationInterceptor jwtValidationInterceptor(JwtUtil jwtUtil) {
    // Return a new interceptor instance with JwtUtil dependency
    return new JwtValidationInterceptor(jwtUtil);
  }
}
