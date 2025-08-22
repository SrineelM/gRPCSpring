package com.poc.grpc.order;

import java.util.Map;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Order Service Application
 *
 * <p>This is the main entry point for the Order Service microservice. It handles order management
 * in the distributed system.
 *
 * <p>Features: 1. gRPC server configuration 2. Database and Redis integration 3. Security
 * configuration 4. Health monitoring
 *
 * <p>Dependencies: - H2/Postgres for persistence - Redis for caching - Spring Security + JWT - gRPC
 * for communication
 *
 * <p>Environment Profiles: - local: H2 database, console logging - dev: File logging, expanded
 * debugging - prod: Structured logging, minimal exposure
 */
@Slf4j
@SpringBootApplication(
    scanBasePackages = {
      "com.poc.grpc.order", // Scans the order-service module
      "com.poc.grpc.common" // Scans the common module for shared components
    },
    exclude = {
      org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
          .class
    })
public class OrderServiceApplication {

  /** The main method which serves as the application's entry point. */
  public static void main(String[] args) {
    try {
      log.info("Starting Order Service");
      SpringApplication app = new SpringApplication(OrderServiceApplication.class);

      // Add default profile if none specified
      if (System.getProperty("spring.profiles.active") == null) {
        log.info("No active profile set, using default profile: local");
        System.setProperty("spring.profiles.active", "local");
      }

      // Start the application
      ConfigurableApplicationContext context = app.run(args);

      // Log successful startup
      String activeProfiles = String.join(", ", context.getEnvironment().getActiveProfiles());
      log.info("Order Service started successfully with profiles: {}", activeProfiles);

      // Log available gRPC services
      logGrpcServices(context);
    } catch (Exception e) {
      log.error("Failed to start Order Service", e);
      throw e;
    }
  }

  /**
   * Logs all registered gRPC services for debugging purposes. This helps verify that all expected
   * services are properly registered.
   *
   * @param context The application context
   */
  private static void logGrpcServices(ConfigurableApplicationContext context) {
    try {
      // Log both types of gRPC service annotations
      Map<String, Object> netDevhServices =
          context.getBeansWithAnnotation(net.devh.boot.grpc.server.service.GrpcService.class);

      if (!netDevhServices.isEmpty()) {
        log.info("Registered gRPC services:");
        netDevhServices.forEach(
            (name, service) -> log.info("  - {} ({})", name, service.getClass().getSimpleName()));
      } else {
        log.warn("No gRPC services found in application context");
      }
    } catch (Exception e) {
      log.error("Error while logging gRPC services", e);
    }
  }

  @Bean
  public ApplicationListener<ApplicationStartedEvent> startupListener() {
    return event -> {
      log.info("Order service started successfully");
    };
  }

  @Bean
  public ApplicationListener<ApplicationReadyEvent> readyListener() {
    return event -> {
      log.info("Order service is ready to accept requests");
    };
  }

  @Bean
  public GrpcServerConfigurer grpcServerConfigurer() {
    return builder -> {
      if (builder instanceof io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder) {
        int processorCount = Runtime.getRuntime().availableProcessors();
        io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder nettyBuilder =
            (io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder) builder;
        nettyBuilder
            .executor(Executors.newFixedThreadPool(processorCount * 2))
            .maxConcurrentCallsPerConnection(100)
            .maxInboundMessageSize(4 * 1024 * 1024);
      }
    };
  }
}
