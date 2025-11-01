package com.poc.grpc.user;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * User Service Application
 *
 * <p>This is the main entry point for the User Service microservice. It handles user management,
 * authentication, and profile operations.
 *
 * <p>Features: 1. User registration and authentication 2. Profile management 3. JWT token
 * generation 4. gRPC service exposure
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
      "com.poc.grpc.user", // Scans the user-service module for components
      "com.poc.grpc.common" // Scans the common module for shared components like exception handlers
      // and security utils
    })
@EnableCaching // Activates Spring's caching abstraction, enabling @Cacheable, @CacheEvict, etc.
public class UserServiceApplication {

  /**
   * The main method which serves as the application's entry point.
   *
   * @param args Command line arguments passed to the application.
   */
  public static void main(String[] args) {
    try {
      log.info("Starting User Service");
      SpringApplication app = new SpringApplication(UserServiceApplication.class);

      // Add default profile if none specified
      if (System.getProperty("spring.profiles.active") == null) {
        log.info("No active profile set, using default profile: local");
        System.setProperty("spring.profiles.active", "local");
      }

      // Start the application
      ConfigurableApplicationContext context = app.run(args);

      // Log successful startup
      String activeProfiles = String.join(", ", context.getEnvironment().getActiveProfiles());
      log.info("User Service started successfully with profiles: {}", activeProfiles);

      // Log available gRPC services
      logGrpcServices(context);
    } catch (Exception e) {
      log.error("Failed to start User Service", e);
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
}
