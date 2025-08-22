-- =================================================================================================
-- Sample Data for the Order Service (H2 Database)
--
-- This script inserts initial data into the tables defined in schema-h2.sql.
-- It is intended for development and testing purposes when using the 'local' profile.
-- =================================================================================================

-- Insert sample orders into the 'orders' table.
INSERT INTO orders (id, user_id, status, total_amount, saga_state, saga_transaction_id, version, created_at, updated_at, created_by, updated_by, payment_id, payment_status, street_address, city, state, postal_code, country)
VALUES
  -- A sample order with a PENDING status.
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'PENDING', 100.00, 'STARTED', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', NULL, NULL, '123 Main St', 'Metropolis', 'State1', '12345', 'Country1'),
  -- A sample order that has been CONFIRMED.
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'CONFIRMED', 200.00, 'COMPLETED', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system', NULL, NULL, '456 Side St', 'Gotham', 'State2', '54321', 'Country2');

-- Insert sample order items into the 'order_items' table, linking them to the orders created above.
INSERT INTO order_items (id, product_id, name, quantity, price, order_id)
VALUES
  -- Two items belonging to the first order ('aaaaaaaa-...').
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'P123', 'Widget', 2, 25.00, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'P456', 'Gadget', 1, 50.00, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
  -- One item belonging to the second order ('bbbbbbbb-...').
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'P789', 'Thingamajig', 4, 50.00, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
