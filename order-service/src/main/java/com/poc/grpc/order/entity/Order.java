package com.poc.grpc.order.entity;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Order Entity
 *
 * <p>This entity represents an order in the system with its associated items and status. It uses
 * UUID as primary key and maintains audit information.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>Bi-directional relationship with OrderItems
 *   <li>Status tracking with enumerated states
 *   <li>Audit fields (created/updated timestamps)
 *   <li>Optimistic locking with version field
 *   <li>Bean validation constraints
 *   <li>Domain logic for order management
 * </ul>
 *
 * <p>Database Table: orders Indexes: - Primary Key: id (UUID) - Foreign Key: user_id (references
 * users.id) - Index: status, created_at
 */
@Entity
@Table(
    name = "orders",
    indexes = {
      @Index(name = "idx_order_status", columnList = "status"),
      @Index(name = "idx_order_created_at", columnList = "created_at"),
      @Index(name = "idx_order_user_id", columnList = "user_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull(message = "User ID is required")
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @NotNull(message = "Order status is required")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @NotNull(message = "Total amount is required")
  @Positive(message = "Total amount must be positive")
  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Valid
  @NotEmpty(message = "Order must have at least one item")
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<OrderItem> items = new ArrayList<>();

  @Size(max = 255, message = "Shipping address must be at most 255 characters")
  @Column(name = "shipping_address")
  private String shippingAddress;

  @Size(max = 50, message = "Payment method must be at most 50 characters")
  @Column(name = "payment_method")
  private String paymentMethod;

  @Version private Long version;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /** Order status enumeration. Represents the possible states of an order in its lifecycle. */
  public enum OrderStatus {
    PENDING, // Initial state when order is created
    CONFIRMED, // Order validated and payment pending
    PROCESSING, // Payment received, being prepared
    SHIPPED, // Order has been shipped
    DELIVERED, // Order successfully delivered
    CANCELLED, // Order cancelled by user or system
    FAILED // Order processing failed
  }

  /** Saga state enumeration. Represents the possible states of a saga in its lifecycle. */
  public enum SagaState {
    NOT_STARTED, // Saga has not been started
    USER_VALIDATED, // User has validated the order
    IN_PROGRESS, // Saga is currently in progress
    COMPLETED, // Saga has been completed
    FAILED, // Saga has failed
    COMPENSATING // Saga is compensating for a failure
  }

  @NotNull(message = "Saga state is required")
  @Enumerated(EnumType.STRING)
  @Column(name = "saga_state", nullable = false)
  @Builder.Default
  private SagaState sagaState = SagaState.NOT_STARTED;

  /**
   * Adds an item to the order and sets up bi-directional relationship. Also recalculates the total
   * amount.
   *
   * @param item The order item to add
   * @throws IllegalArgumentException if the item is null
   */
  public void addItem(OrderItem item) {
    Objects.requireNonNull(item, "Order item cannot be null");
    items.add(item);
    item.setOrder(this);
    recalculateTotal();
  }

  /**
   * Removes an item from the order and breaks bi-directional relationship. Also recalculates the
   * total amount.
   *
   * @param item The order item to remove
   * @throws IllegalArgumentException if the item is null
   */
  public void removeItem(OrderItem item) {
    Objects.requireNonNull(item, "Order item cannot be null");
    items.remove(item);
    item.setOrder(null);
    recalculateTotal();
  }

  /**
   * Recalculates the total amount based on current items. This method should be called whenever
   * items are added or removed.
   */
  private void recalculateTotal() {
    this.totalAmount =
        items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Updates the order status and sets the updated timestamp.
   *
   * @param newStatus The new status to set
   * @throws IllegalArgumentException if the status transition is invalid
   */
  public void updateStatus(OrderStatus newStatus) {
    // Validate status transition
    if (!isValidStatusTransition(this.status, newStatus)) {
      throw new IllegalArgumentException(
          String.format("Invalid status transition from %s to %s", this.status, newStatus));
    }

    this.status = newStatus;
  }

  /**
   * Checks if a status transition is valid.
   *
   * @param currentStatus The current status
   * @param newStatus The new status
   * @return true if the transition is valid
   */
  private boolean isValidStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
    if (currentStatus == newStatus) {
      return true; // Same status is always valid
    }

    switch (currentStatus) {
      case PENDING:
        return newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED;
      case CONFIRMED:
        return newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.CANCELLED;
      case PROCESSING:
        return newStatus == OrderStatus.SHIPPED
            || newStatus == OrderStatus.CANCELLED
            || newStatus == OrderStatus.FAILED;
      case SHIPPED:
        return newStatus == OrderStatus.DELIVERED;
      case DELIVERED:
        return false; // Terminal state
      case CANCELLED:
        return false; // Terminal state
      case FAILED:
        return newStatus == OrderStatus.PROCESSING; // Retry
      default:
        return false;
    }
  }

  /**
   * Updates the saga state of the order.
   *
   * @param sagaState The new saga state to set
   */
  public void setSagaState(SagaState sagaState) {
    this.sagaState = sagaState;
  }

  /**
   * Gets the current saga state of the order.
   *
   * @return The current saga state
   */
  public SagaState getSagaState() {
    return this.sagaState;
  }

  /**
   * Cancels the order if it's in a cancellable state.
   *
   * @return true if the order was cancelled, false if it couldn't be cancelled
   */
  public boolean cancel() {
    if (status == OrderStatus.PENDING
        || status == OrderStatus.CONFIRMED
        || status == OrderStatus.PROCESSING) {
      status = OrderStatus.CANCELLED;
      return true;
    }
    return false;
  }

  /**
   * Checks if the order can be modified.
   *
   * @return true if the order can be modified
   */
  public boolean isModifiable() {
    return status == OrderStatus.PENDING;
  }

  /**
   * Checks if the order is in a terminal state.
   *
   * @return true if the order is in a terminal state
   */
  public boolean isTerminal() {
    return status == OrderStatus.DELIVERED
        || status == OrderStatus.CANCELLED
        || status == OrderStatus.FAILED;
  }
}
// Validation constraints, optimistic locking (@Version), and audit fields are already present.
// No destructive changes needed.
