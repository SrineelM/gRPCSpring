package com.poc.grpc.order.repository;

import com.poc.grpc.order.entity.Order;
import com.poc.grpc.order.entity.Order.OrderStatus;
import com.poc.grpc.order.entity.Order.SagaState;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Order Entity
 *
 * <p>This repository provides data access methods for the Order entity. It extends JpaRepository
 * for basic CRUD operations and adds custom query methods for specific business requirements. It
 * also implements JpaSpecificationExecutor for dynamic query building.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Basic CRUD operations (inherited from JpaRepository)
 *   <li>Custom queries for order management
 *   <li>Pagination support for order listing
 *   <li>Status-based filtering
 *   <li>Dynamic queries via Specifications
 *   <li>Pessimistic locking for critical operations
 *   <li>Saga state management queries
 *   <li>Batch updates for efficient operations
 * </ul>
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Used by OrderService for data persistence
 *   <li>Supports transaction management
 *   <li>Provides optimistic locking via @Version
 *   <li>Supports multi-criteria search via Specifications
 * </ul>
 */
@Repository
public interface OrderRepository
    extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

  /**
   * Finds all orders for a specific user with pagination support. Orders are sorted by creation
   * date in descending order (newest first).
   *
   * @param userId The ID of the user
   * @param pageable Pagination information
   * @return Page of orders
   */
  @Query("SELECT o FROM Order o WHERE o.userId = :userId ORDER BY o.createdAt DESC")
  Page<Order> findByUserId(@Param("userId") UUID userId, Pageable pageable);

  /**
   * Finds all orders for a user with a specific status with pagination support.
   *
   * @param userId The ID of the user
   * @param status The order status to filter by
   * @param pageable Pagination information
   * @return Page of matching orders
   */
  Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

  /**
   * Finds all orders for a user with a specific status.
   *
   * @param userId The ID of the user
   * @param status The order status to filter by
   * @return List of matching orders
   */
  List<Order> findByUserIdAndStatus(UUID userId, OrderStatus status);

  /**
   * Counts the number of orders in a specific status for a user. Used for analytics and user
   * statistics.
   *
   * @param userId The ID of the user
   * @param status The order status to count
   * @return Number of orders
   */
  long countByUserIdAndStatus(UUID userId, OrderStatus status);

  /**
   * Finds orders created within a date range with pagination. Useful for reporting and analytics.
   *
   * @param startDate Start of the date range
   * @param endDate End of the date range
   * @param pageable Pagination information
   * @return Page of orders within the date range
   */
  @Query(
      "SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
  Page<Order> findOrdersInDateRange(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      Pageable pageable);

  /**
   * Finds orders created within a date range. Useful for reporting and analytics.
   *
   * @param startDate Start of the date range
   * @param endDate End of the date range
   * @return List of orders within the date range
   */
  @Query(
      "SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
  List<Order> findOrdersInDateRange(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

  /**
   * Finds orders with a specific saga state. Used for saga management and recovery.
   *
   * @param sagaState The saga state to filter by
   * @param pageable Pagination information
   * @return Page of orders with the specified saga state
   */
  Page<Order> findBySagaState(SagaState sagaState, Pageable pageable);

  /**
   * Finds an order by ID and locks it for update. This method uses pessimistic locking to prevent
   * concurrent modifications in critical operations.
   *
   * @param id The ID of the order to find
   * @return The order, if found
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM Order o WHERE o.id = :id")
  Optional<Order> findByIdWithPessimisticLock(@Param("id") UUID id);

  /**
   * Updates the status of an order. This is a bulk operation that doesn't require loading the
   * entire entity.
   *
   * @param id The ID of the order to update
   * @param status The new status
   * @param updatedAt The timestamp for the update
   * @return The number of rows affected (should be 1 if successful)
   */
  @Modifying
  @Transactional
  @Query("UPDATE Order o SET o.status = :status, o.updatedAt = :updatedAt WHERE o.id = :id")
  int updateOrderStatus(
      @Param("id") UUID id,
      @Param("status") OrderStatus status,
      @Param("updatedAt") LocalDateTime updatedAt);

  /**
   * Updates the saga state of an order. This is a bulk operation that doesn't require loading the
   * entire entity.
   *
   * @param id The ID of the order to update
   * @param sagaState The new saga state
   * @param updatedAt The timestamp for the update
   * @return The number of rows affected (should be 1 if successful)
   */
  @Modifying
  @Transactional
  @Query("UPDATE Order o SET o.sagaState = :sagaState, o.updatedAt = :updatedAt WHERE o.id = :id")
  int updateSagaState(
      @Param("id") UUID id,
      @Param("sagaState") SagaState sagaState,
      @Param("updatedAt") LocalDateTime updatedAt);

  /**
   * Finds orders with a total amount greater than or equal to the specified value.
   *
   * @param amount The minimum total amount
   * @param pageable Pagination information
   * @return Page of orders with a total amount greater than or equal to the specified value
   */
  Page<Order> findByTotalAmountGreaterThanEqual(BigDecimal amount, Pageable pageable);

  /**
   * Finds orders with the specified status that have been in that status longer than the specified
   * duration in hours. Used for identifying stalled orders.
   *
   * @param status The order status
   * @param cutoffTime The cutoff time (orders updated before this time match)
   * @param pageable Pagination information
   * @return Page of potentially stalled orders
   */
  @Query("SELECT o FROM Order o WHERE o.status = :status AND o.updatedAt < :cutoffTime")
  Page<Order> findStalledOrders(
      @Param("status") OrderStatus status,
      @Param("cutoffTime") LocalDateTime cutoffTime,
      Pageable pageable);

  /**
   * Finds orders that need to be processed as part of a saga. These are orders that are in the
   * specified saga state and have been updated before the specified cutoff time.
   *
   * @param sagaState The saga state to filter by
   * @param cutoffTime The cutoff time for considering an order for processing
   * @param limit The maximum number of orders to return
   * @return List of orders that need saga processing
   */
  @Query(
      value =
          "SELECT o FROM Order o WHERE o.sagaState = :sagaState AND o.updatedAt < :cutoffTime ORDER BY o.updatedAt ASC")
  List<Order> findOrdersForSagaProcessing(
      @Param("sagaState") SagaState sagaState,
      @Param("cutoffTime") LocalDateTime cutoffTime,
      Pageable pageable);

  /**
   * Counts orders by status. Used for dashboard statistics.
   *
   * @return List of status counts
   */
  @Query("SELECT o.status as status, COUNT(o) as count FROM Order o GROUP BY o.status")
  List<StatusCount> countOrdersByStatus();

  /** Interface for status count projection results. */
  interface StatusCount {
    OrderStatus getStatus();

    Long getCount();
  }
}
