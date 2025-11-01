# Test Data and Postman Testing Guide

## Overview
This guide provides complete testing instructions using Postman for the gRPC Spring microservices.

---

## Prerequisites

1. **Postman** version 9.0+ with gRPC support
2. **Services Running**:
   - User Service: gRPC on `localhost:9090`, HTTP on `localhost:8080`
   - Order Service: gRPC on `localhost:9091`, HTTP on `localhost:8081`
3. **Server Reflection Enabled** (already configured in local profile)

---

## Postman Setup for gRPC

### Step 1: Create gRPC Request
1. Open Postman → Click **New** → Select **gRPC Request**
2. Enter server URL: `localhost:9090` (for user-service)
3. Postman will auto-discover services via reflection

### Step 2: Import Proto Files (Alternative)
If reflection doesn't work:
1. Go to **APIs** → **Import** → **Upload Files**
2. Import `grpc-common/src/main/proto/user.proto`
3. Import `grpc-common/src/main/proto/order.proto`

---

## Test Scenarios

### 1. User Service Tests (Port 9090)

#### 1.1 Create User (Public - No Auth Required)

**Method:** `com.poc.grpc.user.UserService/CreateUser`

**Request:**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Alice@123",
  "firstName": "Alice",
  "lastName": "Johnson",
  "phoneNumber": "+1-555-0101"
}
```

**Expected Response:**
```json
{
  "userId": "generated-uuid",
  "username": "alice",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Johnson",
  "phoneNumber": "+1-555-0101",
  "isActive": true,
  "isEmailVerified": false,
  "createdAt": "2025-11-01T10:00:00Z",
  "message": "User created successfully"
}
```

#### 1.2 Create Additional Test Users

**User 2 - Bob:**
```json
{
  "username": "bob",
  "email": "bob@example.com",
  "password": "Bob@456",
  "firstName": "Bob",
  "lastName": "Smith"
}
```

**User 3 - Charlie:**
```json
{
  "username": "charlie",
  "email": "charlie@example.com",
  "password": "Charlie@789",
  "firstName": "Charlie",
  "lastName": "Brown"
}
```

#### 1.3 Get User (Requires Authentication)

**Method:** `com.poc.grpc.user.UserService/GetUser`

**Metadata (Add in Postman):**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "userId": "the-user-id-from-create-response"
}
```

**Expected Response:**
```json
{
  "userId": "...",
  "username": "alice",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Johnson",
  "isActive": true,
  "createdAt": "2025-11-01T10:00:00Z"
}
```

#### 1.4 Update User Profile (Requires Authentication)

**Method:** `com.poc.grpc.user.UserService/UpdateUserProfile`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "userId": "your-user-id",
  "firstName": "Alice",
  "lastName": "Johnson-Updated",
  "phoneNumber": "+1-555-0199"
}
```

#### 1.5 Validate User (Service-to-Service)

**Method:** `com.poc.grpc.user.UserService/ValidateUser`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "userId": "user-id-to-validate"
}
```

**Expected Response:**
```json
{
  "valid": true,
  "userId": "...",
  "message": "User is valid and active"
}
```

#### 1.6 Health Check

**Method:** `com.poc.grpc.user.UserService/HealthCheck`

**Request:**
```json
{}
```

**Expected Response:**
```json
{
  "status": "SERVING",
  "message": "User service is healthy"
}
```

---

### 2. Order Service Tests (Port 9091)

#### 2.1 Create Order (Requires Authentication)

**Method:** `com.poc.grpc.order.OrderService/CreateOrder`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "userId": "your-user-id",
  "items": [
    {
      "productId": "PROD-001",
      "name": "Laptop",
      "quantity": 1,
      "price": 999.99
    },
    {
      "productId": "PROD-002",
      "name": "Mouse",
      "quantity": 2,
      "price": 29.99
    }
  ],
  "shippingAddress": "123 Main St, San Francisco, CA 94105",
  "paymentMethod": "CREDIT_CARD"
}
```

**Expected Response:**
```json
{
  "orderId": "generated-order-id",
  "userId": "your-user-id",
  "status": "PENDING",
  "totalAmount": 1059.97,
  "items": [...],
  "shippingAddress": "123 Main St, San Francisco, CA 94105",
  "paymentMethod": "CREDIT_CARD",
  "createdAt": "2025-11-01T10:05:00",
  "updatedAt": "2025-11-01T10:05:00"
}
```

#### 2.2 Get Order (Requires Authentication)

**Method:** `com.poc.grpc.order.OrderService/GetOrder`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "orderId": "order-id-from-create"
}
```

#### 2.3 List User Orders (Requires Authentication)

**Method:** `com.poc.grpc.order.OrderService/ListUserOrders`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "userId": "your-user-id",
  "pageSize": 10,
  "pageNumber": 0
}
```

**Expected Response:**
```json
{
  "orders": [
    {
      "orderId": "...",
      "status": "PENDING",
      "totalAmount": 1059.97,
      ...
    }
  ],
  "totalPages": 1,
  "totalItems": 1,
  "currentPage": 0
}
```

#### 2.4 Update Order Status (Requires Authentication)

**Method:** `com.poc.grpc.order.OrderService/UpdateOrderStatus`

**Metadata:**
```
authorization: Bearer <your-jwt-token>
```

**Request:**
```json
{
  "orderId": "your-order-id",
  "status": "CONFIRMED"
}
```

**Valid Status Values:**
- `PENDING`
- `CONFIRMED`
- `PROCESSING`
- `SHIPPED`
- `DELIVERED`
- `CANCELLED`

#### 2.5 Health Check

**Method:** `com.poc.grpc.order.OrderService/HealthCheck`

**Request:**
```json
{}
```

---

## Error Scenarios

### 1. Missing Authentication Token

**Request:** Any authenticated endpoint without authorization header

**Expected Error:**
```
Status: UNAUTHENTICATED (16)
Message: "No authorization header found"
```

### 2. Invalid JWT Token

**Metadata:**
```
authorization: Bearer invalid-token-12345
```

**Expected Error:**
```
Status: UNAUTHENTICATED (16)
Message: "Invalid JWT signature" or "Malformed JWT token"
```

### 3. Expired JWT Token

**Expected Error:**
```
Status: UNAUTHENTICATED (16)
Message: "Expired JWT token"
```

### 4. User Not Found

**Request:** GetUser with non-existent userId

**Expected Error:**
```
Status: NOT_FOUND (5)
Message: "User not found with ID: <id>"
```

### 5. Duplicate Username

**Request:** CreateUser with existing username

**Expected Error:**
```
Status: ALREADY_EXISTS (6)
Message: "Username already exists"
```

### 6. Invalid Input

**Request:** CreateUser with empty username

**Expected Error:**
```
Status: INVALID_ARGUMENT (3)
Message: "Username cannot be empty"
```

### 7. Order Not Found

**Request:** GetOrder with non-existent orderId

**Expected Error:**
```
Status: NOT_FOUND (5)
Message: "Order not found with ID: <id>"
```

---

## Postman Collection Variables

Set up these variables in Postman for easier testing:

```json
{
  "user_service_grpc": "localhost:9090",
  "order_service_grpc": "localhost:9091",
  "user_service_http": "http://localhost:8080",
  "order_service_http": "http://localhost:8081",
  "jwt_token": "<paste-token-here>",
  "user_id": "<your-user-id>",
  "order_id": "<your-order-id>"
}
```

---

## Testing with grpcurl (Command Line)

### Install grpcurl
```bash
# macOS
brew install grpcurl

# Linux
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest
```

### List Services
```bash
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9091 list
```

### Create User
```bash
grpcurl -plaintext -d '{
  "username": "testuser",
  "email": "test@example.com",
  "password": "Test@123",
  "firstName": "Test",
  "lastName": "User"
}' localhost:9090 com.poc.grpc.user.UserService/CreateUser
```

### Get User (with JWT)
```bash
grpcurl -plaintext \
  -H "authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"userId": "USER_ID"}' \
  localhost:9090 com.poc.grpc.user.UserService/GetUser
```

### Create Order (with JWT)
```bash
grpcurl -plaintext \
  -H "authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": "USER_ID",
    "items": [
      {"productId": "P1", "name": "Product", "quantity": 1, "price": 99.99}
    ],
    "shippingAddress": "123 Main St",
    "paymentMethod": "CARD"
  }' \
  localhost:9091 com.poc.grpc.order.OrderService/CreateOrder
```

---

## HTTP Actuator Endpoints

### User Service (Port 8080)
```bash
# Health Check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Prometheus
curl http://localhost:8080/actuator/prometheus
```

### Order Service (Port 8081)
```bash
# Health Check
curl http://localhost:8081/actuator/health

# Metrics
curl http://localhost:8081/actuator/metrics
```

---

## Performance Testing Data

### Bulk User Creation
Create multiple users for load testing:
```json
[
  {"username": "user001", "email": "user001@test.com", "password": "Pass@001", "firstName": "User", "lastName": "001"},
  {"username": "user002", "email": "user002@test.com", "password": "Pass@002", "firstName": "User", "lastName": "002"},
  // ... up to user100
]
```

### Bulk Order Creation
```json
{
  "userId": "test-user-id",
  "items": [
    {"productId": "PROD-001", "name": "Item1", "quantity": 5, "price": 10.00},
    {"productId": "PROD-002", "name": "Item2", "quantity": 3, "price": 25.00},
    {"productId": "PROD-003", "name": "Item3", "quantity": 2, "price": 50.00}
  ],
  "shippingAddress": "Test Address",
  "paymentMethod": "TEST"
}
```

---

## Database Verification (H2 Console)

### Access H2 Console
- User Service: http://localhost:8080/h2-console
- Order Service: http://localhost:8081/h2-console

### Connection Settings
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (leave empty)

### Verify User Creation
```sql
SELECT * FROM users;
SELECT * FROM user_profiles;
```

### Verify Order Creation
```sql
SELECT * FROM orders;
SELECT * FROM order_items;
```

---

## Monitoring and Observability

### Check Logs
Logs include correlation IDs for distributed tracing:
```
2025-11-01 10:00:00 [correlationId=abc-123] INFO  Creating user: alice
2025-11-01 10:00:01 [correlationId=abc-123] INFO  User created successfully
```

### Check Metrics
```bash
# Circuit breaker state
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state

# gRPC call metrics
curl http://localhost:8080/actuator/metrics/grpc.server.calls

# JVM memory
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

---

## Tips for Effective Testing

1. **Start Simple**: Begin with health checks, then create users, then orders
2. **Save Tokens**: Copy JWT tokens to Postman variables for reuse
3. **Check Logs**: Monitor application logs for detailed error messages
4. **Use H2 Console**: Verify data persistence in the database
5. **Test Error Cases**: Don't just test happy paths
6. **Monitor Resources**: Watch memory and CPU usage during testing
7. **Use Correlation IDs**: Track requests across services using log correlation IDs

---

## Troubleshooting

### Postman Can't Discover Services
- Ensure server reflection is enabled: `grpc.server.enable-reflection: true`
- Check if service is running: `grpcurl -plaintext localhost:9090 list`
- Manually import proto files if reflection fails

### Authentication Fails
- Verify JWT token is not expired (24-hour validity)
- Check authorization header format: `Bearer <token>`
- Ensure JWT_SECRET environment variable is set consistently

### Connection Refused
- Verify correct ports: User=9090, Order=9091
- Check if services are running: `./gradlew :user-service:bootRun`
- Ensure no firewall blocking

### Data Not Persisting
- H2 is in-memory; data resets on service restart
- For persistence, switch to PostgreSQL profile

---

*Last Updated: November 1, 2025*
