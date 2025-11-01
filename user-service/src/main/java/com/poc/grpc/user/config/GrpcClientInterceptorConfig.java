package com.poc.grpc.user.config;

import com.poc.grpc.common.security.JwtAuthorizationInterceptor;
import com.poc.grpc.common.security.JwtUtil;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JwtAuthorizationInterceptor as a global gRPC client interceptor for the user
 * service. This ensures that all outgoing gRPC requests from the user service will have full JWT
 * authorization applied.
 *
 * <p>Beginners: This is the recommended way to add a gRPC client interceptor in Spring Boot with
 * grpc-spring-boot-starter.
 */
@Configuration
public class GrpcClientInterceptorConfig {

  @Bean
  @GrpcGlobalClientInterceptor
  public JwtAuthorizationInterceptor jwtAuthorizationInterceptor(JwtUtil jwtUtil) {
    // Construct the interceptor with its dependency, not by injecting itself
    return new JwtAuthorizationInterceptor(jwtUtil);
  }
}
