package com.poc.grpc.order.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * Order Item Entity
 *
 * <p>This entity represents an individual item within an order. It maintains a bi-directional
 * relationship with the Order entity and stores product details at the time of order creation.
 *
 * <p>Key Features: 1. Bi-directional relationship with Order 2. Immutable product information 3.
 * Price and quantity tracking
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
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false)
  private String productId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

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
   */
  public int updateQuantity(int newQuantity) {
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
   */
  public OrderItem copyWithQuantity(int newQuantity) {
    return OrderItem.builder()
        .productId(this.productId)
        .name(this.name)
        .quantity(newQuantity)
        .price(this.price)
        .build();
  }
}
