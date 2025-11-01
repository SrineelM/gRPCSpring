package com.poc.grpc.common.config;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * gRPC Client Configuration
 *
 * <p>This configuration class sets up gRPC clients for inter-service communication. It configures
 * channels, interceptors, and connection management.
 *
 * <p>Features: 1. Secure channel configuration 2. JWT authentication interceptor 3. Connection
 * pooling 4. Retry and timeout policies
 *
 * <p>Service Endpoints: - User Service: Authentication and profile management - Order Service:
 * Order processing and management
 *
 * <p>Security: - JWT token propagation - TLS for production - Connection timeouts
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GrpcClientConfig {

  // Injected Spring Environment to access application properties (e.g., host, port).
  private final Environment env;
  // Injected client interceptors, typically for handling cross-cutting concerns like authentication
  // and metrics.
  private final java.util.List<ClientInterceptor> interceptors;

  /**
   * Generic factory for gRPC ManagedChannel using properties. Usage: inject this bean and call
   * createChannel("user-service") or any other service name.
   */
  public ManagedChannel createChannel(String serviceName) {
    String base = "grpc.client." + serviceName + ".";
    String address = env.getProperty(base + "address");
    if (address == null) {
      throw new IllegalArgumentException("No gRPC address configured for service: " + serviceName);
    }
    String negotiationType =
        env.getProperty(base + "negotiation-type", "PLAINTEXT").toUpperCase(Locale.ROOT);

    log.info(
        "Configuring gRPC channel for {} at {} (negotiation: {})",
        serviceName,
        address,
        negotiationType);

    ManagedChannelBuilder<?> builder =
        ManagedChannelBuilder.forTarget(address)
            .intercept(interceptors)
            .maxInboundMessageSize(20 * 1024 * 1024)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .idleTimeout(60, TimeUnit.SECONDS);

    switch (negotiationType) {
      case "TLS":
        builder.useTransportSecurity();
        break;
      case "PLAINTEXT":
      default:
        builder.usePlaintext();
        break;
    }

    log.debug("{} channel configured with keepalive: 30s, timeout: 10s", serviceName);
    return builder.build();
  }

  // Removed jwtClientInterceptor bean as all interceptors are now injected as a list.
}
