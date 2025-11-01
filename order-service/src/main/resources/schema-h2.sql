-- =================================================================================================
-- Schema for the Order Service (H2 Database)
--
-- This script defines the table structure for the order-service when running with the 'local'
-- profile, which uses an in-memory H2 database. It is intended for development and testing
-- purposes.
--
-- For other environments (QA, Staging, Prod), schema management should be handled by a
-- migration tool like Flyway or Liquibase.
-- =================================================================================================

-- Table to store order details.
CREATE TABLE orders (
    -- Primary key for the order, using UUID for global uniqueness to avoid collisions in a distributed environment.
    id UUID PRIMARY KEY,
    -- Foreign key linking to the user who placed the order. This assumes a separate user service manages user data.
    user_id UUID NOT NULL,
    -- Current status of the order (e.g., PENDING, CONFIRMED, SHIPPED, CANCELLED).
    status VARCHAR(32) NOT NULL,
    -- The total calculated amount for the entire order. Using DECIMAL for precision with monetary values.
    total_amount DECIMAL(19,2) NOT NULL,
    -- The current state of the saga pattern transaction for this order, used for distributed transactions.
    saga_state VARCHAR(32),
    -- A unique identifier for the saga transaction, if applicable, to correlate events across services.
    saga_transaction_id UUID,
    -- Version number for optimistic locking to prevent concurrent modification issues.
    version BIGINT,
    -- Timestamp when the order was created.
    created_at TIMESTAMP NOT NULL,
    -- Timestamp when the order was last updated.
    updated_at TIMESTAMP,
    -- Identifier of the user or system that created the record for auditing purposes.
    created_by VARCHAR(255),
    -- Identifier of the user or system that last updated the record for auditing purposes.
    updated_by VARCHAR(255),
    -- The ID of the payment transaction associated with this order, linking to a payment service.
    payment_id VARCHAR(255),
    -- The status of the payment (e.g., PENDING, SUCCESS, FAILED), synchronized from the payment service.
    payment_status VARCHAR(32),
    -- Shipping address details.
    street_address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100)
);

-- Indexes to improve query performance on frequently queried columns.
-- Index on user_id for fast retrieval of a user's orders.
CREATE INDEX idx_user_id ON orders(user_id);
-- Index on status for efficient querying of orders by their status.
CREATE INDEX idx_status ON orders(status);
-- Index on created_at for time-based queries and sorting.
CREATE INDEX idx_created_at ON orders(created_at);
-- Index on saga_state to quickly find orders in a specific state of a distributed transaction.
CREATE INDEX idx_saga_state ON orders(saga_state);

-- Table to store individual items within an order.
CREATE TABLE order_items (
    -- Primary key for the order item, using UUID for global uniqueness.
    id UUID PRIMARY KEY,
    -- The unique identifier for the product being ordered. This could be a SKU or other product code.
    product_id VARCHAR(255) NOT NULL,
    -- The name of the product at the time of order.
    name VARCHAR(255) NOT NULL,
    -- The number of units of this product in the order.
    quantity INT NOT NULL,
    -- The price per unit of the product at the time of order. Stored here to avoid issues if product prices change later.
    price DECIMAL(19,2) NOT NULL,
    -- Foreign key linking this item to its parent order.
    order_id UUID NOT NULL,
    -- Constraint to ensure referential integrity with the 'orders' table. Deleting an order will cascade and delete its items.
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
