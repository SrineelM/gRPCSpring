package com.poc.grpc.order.service;

import com.google.protobuf.Timestamp;
import com.poc.grpc.order.*;
import com.poc.grpc.order.entity.Order;
import com.poc.grpc.order.entity.Order.SagaState;
import com.poc.grpc.order.entity.OrderItem;
import com.poc.grpc.order.repository.OrderRepository;
import com.poc.grpc.user.UserServiceGrpc;
import com.poc.grpc.user.ValidateUserRequest;
import com.poc.grpc.user.ValidateUserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

  private final OrderRepository orderRepository;
  private final RedisTemplate<String, Object> redisTemplate;

  @GrpcClient("user-service")
  private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

  @Override
  @Transactional
  @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
  @Retry(name = "orderService")
  @TimeLimiter(name = "orderService")
  public void createOrder(
      CreateOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
    String userId = request.getUserId();
    log.info("Starting order creation for user: {}", userId);
    log.debug(
        "Order details - Items count: {}, Shipping address: {}",
        request.getItemsCount(),
        request.getShippingAddress());

    try {
      if (request.getItemsCount() == 0) {
        log.warn("Order creation failed - No items in order for user: {}", userId);
        throw new IllegalArgumentException("Order must contain at least one item");
      }

      Order order = buildOrderFromRequest(request);
      log.debug("Order entity created with {} items", order.getItems().size());

      Order savedOrder = orderRepository.save(order);
      log.info(
          "Order saved successfully - ID: {}, Total Amount: {}",
          savedOrder.getId(),
          savedOrder.getTotalAmount());

      OrderResponse response = buildOrderResponse(savedOrder);
      responseObserver.onNext(response);
      responseObserver.onCompleted();

      cacheOrder(savedOrder);
      log.debug("Order cached successfully - ID: {}", savedOrder.getId());
    } catch (Exception e) {
      log.error("Order creation failed for user: {} - Error: {}", userId, e.getMessage(), e);
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Failed to create order: " + e.getMessage())
              .asRuntimeException());
    }
  }

  // Renamed to avoid method erasure clash
  public void streamUserOrders(
      ListUserOrdersRequest request, StreamObserver<OrderResponse> responseObserver) {
    String userId = request.getUserId();

    try {
      PageRequest pageRequest =
          PageRequest.of(
              request.getPageNumber(),
              request.getPageSize(),
              Sort.by(Sort.Direction.DESC, "createdAt"));

      Page<Order> orders = orderRepository.findByUserId(UUID.fromString(userId), pageRequest);

      orders
          .getContent()
          .forEach(
              order -> {
                OrderResponse response = convertToOrderResponse(order);
                responseObserver.onNext(response);
              });

      responseObserver.onCompleted();
      log.info("Retrieved {} orders for user: {}", orders.getTotalElements(), userId);

    } catch (Exception e) {
      log.error("Failed to retrieve order history for user: {}", userId, e);
      responseObserver.onError(
          Status.INTERNAL
              .withDescription("Failed to retrieve order history")
              .withCause(e)
              .asRuntimeException());
    }
  }

  @Override
  public void getOrder(GetOrderRequest request, StreamObserver<OrderResponse> responseObserver) {
    String userId = "temp-user-id"; // TODO: Get from JWT token

    try {
      String cacheKey = "order:" + request.getOrderId();
      OrderResponse cachedResponse = (OrderResponse) redisTemplate.opsForValue().get(cacheKey);

      if (cachedResponse != null) {
        log.debug("Cache HIT for order: {}", request.getOrderId());
        responseObserver.onNext(cachedResponse);
        responseObserver.onCompleted();
        return;
      }
      log.debug("Cache MISS for order: {}", request.getOrderId());

      Order order =
          orderRepository
              .findById(UUID.fromString(request.getOrderId()))
              .filter(o -> o.getUserId().toString().equals(userId))
              .orElseThrow(() -> new RuntimeException("Order not found or access denied"));

      OrderResponse response = convertToOrderResponse(order);

      redisTemplate.opsForValue().set(cacheKey, response, java.time.Duration.ofMinutes(15));

      responseObserver.onNext(response);
      responseObserver.onCompleted();

    } catch (Exception e) {
      log.error("Failed to retrieve order: {}", request.getOrderId(), e);
      responseObserver.onError(
          Status.NOT_FOUND
              .withDescription("Order not found or access denied")
              .withCause(e)
              .asRuntimeException());
    }
  }

  @Async
  @CircuitBreaker(name = "sagaOrchestration")
  public CompletableFuture<Order> orchestrateSaga(Order order) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            validateUserStep(order);
            completeOrderStep(order);
            return order;
          } catch (Exception e) {
            log.error("Saga failed for order: {}", order.getId(), e);
            throw new RuntimeException("Saga execution failed", e);
          }
        });
  }

  private void validateUserStep(Order order) {
    try {
      log.info("Saga Step: Validating user for order: {}", order.getId());
      ValidateUserResponse response =
          userServiceStub
              .withDeadlineAfter(2, TimeUnit.SECONDS)
              .validateUser(
                  ValidateUserRequest.newBuilder().setUserId(order.getUserId().toString()).build());

      if (!response.getValid()) {
        throw new RuntimeException("User validation check returned invalid");
      }

      order.setSagaState(SagaState.USER_VALIDATED);
      orderRepository.save(order);
      log.info("User validation completed for order: {}", order.getId());

    } catch (Exception e) {
      order.setSagaState(SagaState.FAILED);
      orderRepository.save(order);
      throw new RuntimeException("User validation step failed", e);
    }
  }

  private void completeOrderStep(Order order) {
    order.setStatus(com.poc.grpc.order.entity.Order.OrderStatus.CONFIRMED);
    order.setSagaState(SagaState.COMPLETED);
    orderRepository.save(order);
    log.info("Order completed successfully: {}", order.getId());
  }

  @Async
  public void compensateSaga(Order order) {
    try {
      log.warn("Starting SAGA compensation for order: {}", order.getId());
      order.setSagaState(SagaState.COMPENSATING);

      order.setStatus(com.poc.grpc.order.entity.Order.OrderStatus.CANCELLED);
      order.setSagaState(SagaState.FAILED);
      orderRepository.save(order);

      log.info("Saga compensation completed for order: {}", order.getId());
    } catch (Exception e) {
      log.error(
          "FATAL: Saga compensation FAILED for order: {}. Manual intervention required.",
          order.getId(),
          e);
    }
  }

  private Order buildOrderFromRequest(CreateOrderRequest request) {
    log.debug("Building order entity from request for user: {}", request.getUserId());

    Order order =
        Order.builder()
            .userId(UUID.fromString(request.getUserId()))
            .status(com.poc.grpc.order.entity.Order.OrderStatus.PENDING)
            .build();

    request
        .getItemsList()
        .forEach(
            item -> {
              OrderItem orderItem =
                  OrderItem.builder()
                      .productId(item.getProductId())
                      .quantity(item.getQuantity())
                      .price(BigDecimal.valueOf(item.getPrice()))
                      .name(item.getName())
                      .order(order)
                      .build();
              order.addItem(orderItem);
            });

    BigDecimal total =
        order.getItems().stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    order.setTotalAmount(total);

    log.debug(
        "Order entity built with {} items, total amount: {}",
        order.getItems().size(),
        order.getTotalAmount());
    return order;
  }

  private OrderResponse buildOrderResponse(Order order) {
    log.debug("Building order response for order: {}", order.getId());

    OrderResponse.Builder responseBuilder =
        OrderResponse.newBuilder()
            .setOrderId(order.getId().toString())
            .setUserId(order.getUserId().toString())
            .setStatus(order.getStatus().toString())
            .setTotalAmount(order.getTotalAmount().doubleValue())
            .setCreatedAt(order.getCreatedAt().toString());

    order
        .getItems()
        .forEach(
            item -> {
              OrderItemResponse itemResponse =
                  OrderItemResponse.newBuilder()
                      .setProductId(item.getProductId())
                      .setQuantity(item.getQuantity())
                      .setPrice(item.getPrice().doubleValue())
                      .setName(item.getName())
                      .build();
              responseBuilder.addItems(itemResponse);
            });

    return responseBuilder.build();
  }

  private void cacheOrder(Order order) {
    try {
      String cacheKey = "order:" + order.getId();
      redisTemplate.opsForValue().set(cacheKey, order, Duration.ofHours(24));
      log.debug("Order cached successfully with key: {}", cacheKey);
    } catch (Exception e) {
      log.warn("Failed to cache order: {} - Error: {}", order.getId(), e.getMessage());
    }
  }

  // Only one definition, using String for createdAt/updatedAt
  private OrderResponse convertToOrderResponse(Order order) {
    return OrderResponse.newBuilder()
        .setOrderId(order.getId().toString())
        .setUserId(order.getUserId().toString())
        .setStatus(convertStatus(order.getStatus()))
        .setTotalAmount(order.getTotalAmount().doubleValue())
        .setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : "")
        .setUpdatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : "")
        .build();
  }

  private com.poc.grpc.order.OrderItem convertOrderItem(OrderItem item) {
    return com.poc.grpc.order.OrderItem.newBuilder()
        .setProductId(item.getProductId())
        .setName(item.getName())
        .setQuantity(item.getQuantity())
        .setPrice(item.getPrice().doubleValue())
        .build();
  }

  private Timestamp toTimestamp(LocalDateTime time) {
    if (time == null) return Timestamp.getDefaultInstance();
    return Timestamp.newBuilder().setSeconds(time.toEpochSecond(ZoneOffset.UTC)).build();
  }

  private String convertStatus(com.poc.grpc.order.entity.Order.OrderStatus status) {
    return status.name();
  }

  public void createOrderFallback(
      CreateOrderRequest request, StreamObserver<OrderResponse> responseObserver, Exception ex) {
    log.error(
        "Circuit breaker opened for createOrder. Fallback triggered for user: {}",
        request.getUserId(),
        ex);
    responseObserver.onError(
        Status.UNAVAILABLE
            .withDescription("Order service is temporarily unavailable. Please try again later.")
            .withCause(ex)
            .asRuntimeException());
  }
}
