package com.poc.grpc.order.service;

import com.poc.grpc.order.repository.OrderRepository;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * gRPC Health Check Service
 *
 * <p>This service implements the standard gRPC health checking protocol. It provides health status
 * information for the order service and its dependencies.
 *
 * <p>Features: 1. Standard gRPC health check implementation 2. Database connectivity check 3. Redis
 * connection monitoring 4. Dependency status reporting
 *
 * <p>Health States: - SERVING: Service is fully operational - NOT_SERVING: Service is not
 * operational - UNKNOWN: Status cannot be determined
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class HealthService extends HealthGrpc.HealthImplBase {

  private final OrderRepository orderRepository;
  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public void check(
      HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
    log.debug("Received health check request for service: {}", request.getService());

    boolean isDbHealthy = checkDatabase();
    boolean isRedisHealthy = checkRedis();

    ServingStatus status =
        isDbHealthy && isRedisHealthy ? ServingStatus.SERVING : ServingStatus.NOT_SERVING;

    HealthCheckResponse response = HealthCheckResponse.newBuilder().setStatus(status).build();

    if (status == ServingStatus.SERVING) {
      log.info("Health check completed successfully - Status: SERVING");
    } else {
      log.warn("Health check completed with failures - Status: NOT_SERVING");
    }

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  private boolean checkDatabase() {
    try {
      orderRepository.count();
      log.debug("Database check passed");
      return true;
    } catch (Exception e) {
      log.error("Database health check failed: {}", e.getMessage(), e);
      return false;
    }
  }

  private boolean checkRedis() {
    try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.ping();
      log.debug("Redis check passed");
      return true;
    } catch (Exception e) {
      log.error("Redis health check failed: {}", e.getMessage(), e);
      return false;
    }
  }

  @Override
  public void watch(
      HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
    log.warn("Health watch request received but not implemented");
    responseObserver.onError(
        Status.UNIMPLEMENTED
            .withDescription("Health watch is not implemented")
            .asRuntimeException());
  }
}
