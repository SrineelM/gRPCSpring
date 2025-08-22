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
@Configuration
public class GrpcClientInterceptorConfig {

  @Bean
  @GrpcGlobalClientInterceptor
  public JwtValidationInterceptor jwtValidationInterceptor(JwtUtil jwtUtil) {
    // Construct the interceptor with its dependency, not by injecting itself
    return new JwtValidationInterceptor(jwtUtil);
  }
}
