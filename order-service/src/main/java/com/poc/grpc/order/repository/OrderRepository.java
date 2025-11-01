package com.poc.grpc.order.repository;

import com.poc.grpc.order.entity.Order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Order Entity
 *
 * <p>This repository provides data access methods for the Order entity. It extends JpaRepository
 * for basic CRUD operations and adds custom query methods for specific business requirements.
 *
 * <p>Features: 1. Basic CRUD operations (inherited from JpaRepository) 2. Custom queries for order
 * management 3. Pagination support for order listing 4. Status-based filtering
 *
 * <p>Usage: - Used by OrderService for data persistence - Supports transaction management -
 * Provides optimistic locking via @Version
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

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
   * Finds all orders for a user with a specific status. Useful for filtering orders in different
   * states.
   *
   * @param userId The ID of the user
   * @param status The order status to filter by
   * @return List of matching orders
   */
  List<Order> findByUserIdAndStatus(UUID userId, Order.OrderStatus status);

  /**
   * Counts the number of orders in a specific status for a user. Used for analytics and user
   * statistics.
   *
   * @param userId The ID of the user
   * @param status The order status to count
   * @return Number of orders
   */
  long countByUserIdAndStatus(UUID userId, Order.OrderStatus status);

  /**
   * Finds orders created within a date range. Useful for reporting and analytics.
   *
   * @param startDate Start of the date range
   * @param endDate End of the date range
   * @return List of orders within the date range
   */
  @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
  List<Order> findOrdersInDateRange(
      @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
