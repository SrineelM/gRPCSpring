package com.poc.grpc.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Order Item Entity
 *
 * <p>This entity represents an individual item within an order. It maintains a bi-directional
 * relationship with the Order entity and stores product details at the time of order creation.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>Bi-directional relationship with Order
 *   <li>Immutable product information
 *   <li>Price and quantity tracking
 *   <li>Bean validation constraints
 *   <li>Optimistic locking
 *   <li>Audit timestamps
 * </ul>
 *
 * <p>Database Table: order_items Indexes: - Primary Key: id (UUID) - Foreign Key: order_id
 * (references orders.id)
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @NotBlank(message = "Product ID is required")
  @Column(name = "product_id", nullable = false)
  private String productId;

  @NotBlank(message = "Product name is required")
  @Column(nullable = false)
  private String name;

  @NotNull(message = "Quantity is required")
  @Min(value = 1, message = "Quantity must be at least 1")
  @Column(nullable = false)
  private Integer quantity;

  @NotNull(message = "Price is required")
  @Positive(message = "Price must be positive")
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Version private Long version;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  /**
   * Calculates the total price for this item. Total is quantity multiplied by unit price.
   *
   * @return The total price for this item
   */
  public BigDecimal calculateTotal() {
    return price.multiply(BigDecimal.valueOf(quantity));
  }

  /**
   * Updates the quantity and returns the difference. This is useful for inventory management.
   *
   * @param newQuantity The new quantity to set
   * @return The difference (positive if increased, negative if decreased)
   * @throws IllegalArgumentException if the new quantity is less than 1
   */
  public int updateQuantity(int newQuantity) {
    if (newQuantity < 1) {
      throw new IllegalArgumentException("Quantity must be at least 1");
    }
    int difference = newQuantity - this.quantity;
    this.quantity = newQuantity;
    return difference;
  }

  /**
   * Creates a copy of this item with a new quantity. Useful for order splitting or partial
   * fulfillment.
   *
   * @param newQuantity The quantity for the new item
   * @return A new OrderItem with the same details but different quantity
   * @throws IllegalArgumentException if the new quantity is less than 1
   */
  public OrderItem copyWithQuantity(int newQuantity) {
    if (newQuantity < 1) {
      throw new IllegalArgumentException("Quantity must be at least 1");
    }
    return OrderItem.builder()
        .productId(this.productId)
        .name(this.name)
        .quantity(newQuantity)
        .price(this.price)
        .build();
  }

  /**
   * Checks if this item is for the same product as another item. This can be used to merge items
   * when adding to cart.
   *
   * @param other The other order item to compare with
   * @return true if both items refer to the same product
   */
  public boolean isSameProductAs(OrderItem other) {
    return this.productId != null && this.productId.equals(other.getProductId());
  }

  /**
   * Merges this item with another item by adding quantities. Items must be for the same product.
   *
   * @param other The other order item to merge with
   * @throws IllegalArgumentException if items are for different products
   */
  public void mergeWith(OrderItem other) {
    if (!isSameProductAs(other)) {
      throw new IllegalArgumentException("Cannot merge items for different products");
    }
    this.quantity += other.getQuantity();
  }
}
