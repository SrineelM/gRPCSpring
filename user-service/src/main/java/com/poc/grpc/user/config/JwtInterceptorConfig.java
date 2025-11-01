package com.poc.grpc.user.config;

import com.poc.grpc.common.security.JwtAuthorizationInterceptor;
import com.poc.grpc.common.security.JwtUtil;
import io.grpc.ClientInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the JWT Authorization Interceptor for the user service. This interceptor generates and
 * attaches JWT tokens to outgoing gRPC requests.
 *
 * <p>By defining this bean, the shared GrpcClientConfig from grpc-common will use it automatically.
 */
@Configuration
public class JwtInterceptorConfig {
  @Bean
  public ClientInterceptor jwtInterceptor(JwtUtil jwtUtil) {
    return new JwtAuthorizationInterceptor(jwtUtil);
  }
}
