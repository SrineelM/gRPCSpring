package com.poc.grpc.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * <p>Key Features: 1. Bi-directional relationship with OrderItems 2. Status tracking with
 * enumerated states 3. Audit fields (created/updated timestamps) 4. Optimistic locking with version
 * field
 *
 * <p>Database Table: orders Indexes: - Primary Key: id (UUID) - Foreign Key: user_id (references
 * users.id) - Index: status, created_at
 */
@Entity
@Table(name = "orders")
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

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<OrderItem> items = new ArrayList<>();

  @Column(name = "shipping_address")
  private String shippingAddress;

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

  // In `Order.java`
  @Builder.Default private SagaState sagaState = SagaState.NOT_STARTED;

  /**
   * Adds an item to the order and sets up bi-directional relationship. Also recalculates the total
   * amount.
   *
   * @param item The order item to add
   */
  public void addItem(OrderItem item) {
    items.add(item);
    item.setOrder(this);
    recalculateTotal();
  }

  /**
   * Removes an item from the order and breaks bi-directional relationship. Also recalculates the
   * total amount.
   *
   * @param item The order item to remove
   */
  public void removeItem(OrderItem item) {
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
   */
  public void updateStatus(OrderStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = LocalDateTime.now();
  }

  public void setSagaState(SagaState sagaState) {
    this.sagaState = sagaState;
  }

  public SagaState getSagaState() {
    return this.sagaState;
  }
}
