# gRPCSpring Microservices Demo

## Overview

This project demonstrates a modern microservices architecture using **Spring Boot** and **gRPC**. It is designed for students and developers who want to learn how to build scalable, secure, and efficient distributed systems in Java.

The system consists of two main services:
- **user-service**: Manages user registration, authentication, and profiles.
- **order-service**: Handles order creation, management, and history for users.
- **grpc-common**: Contains shared code, Protobuf definitions, security, and exception handling logic.

All services communicate using **gRPC** for high-performance, strongly-typed remote procedure calls.

---

## Features

### Core Features
- **Spring Boot** for rapid development and production-ready features
- **gRPC** for fast, contract-based inter-service communication
- **H2 in-memory database** for easy local development (no external DB required)
- **Redis** for caching and performance (optional, but recommended)
- **JWT authentication** for secure, stateless APIs
- **Centralized exception handling** and health checks
- **Docker Compose** for easy infrastructure setup (Postgres/Redis, optional)

### Monitoring & Debugging
- **Comprehensive Logging:**
  - Local: Colored console output for better readability
  - Dev/QA/Prod: File-based logging with rotation
  - Structured log format with correlation IDs
  - Different log levels per environment
- **Health Checks:**
  - gRPC health probes
  - Database connectivity checks
  - Redis cache monitoring
- **Metrics & Tracing:**
  - Spring Actuator endpoints
  - Custom health indicators
  - Distributed tracing support

### Security Features
- JWT-based authentication
- Role-based access control
- Secure communication between services
- Token validation at service boundaries

---

## Architecture

```
[user-service] <---- gRPC ----> [order-service]
      |                             |
      |                             |
   [H2 DB]                     [H2 DB]
      |                             |
   [Redis] <--- shared --->   [Redis]
```

### Key Components
- Each service has its own database and can scale independently
- Shared logic (Protobuf, security, exception handling) lives in `grpc-common`
- Services communicate via gRPC with JWT authentication
- Redis provides distributed caching

---

## Tech Stack

### Core Technologies
- Java 17+
- Spring Boot 3.x
- gRPC (via grpc-spring-boot-starter)
- H2 (in-memory DB for local/dev)
- Redis (for caching)
- JWT (JSON Web Tokens) for authentication
- Gradle (multi-module build)

### Development Tools
- Gradle for build management
- Docker Compose for infrastructure
- grpcurl for API testing
- H2 Console for database inspection

---

## Getting Started (Local Development)

### Prerequisites
- Java 17+
- Redis (optional, but recommended)
- grpcurl (for testing)

### 1. **Clone the Repository**
```sh
git clone <your-repo-url>
cd gRPCSpring
```

### 2. **Run Redis Locally (Optional, Recommended)**
- **macOS:** `brew install redis && brew services start redis`
- **Linux:** `sudo apt-get install redis-server && sudo service redis-server start`
- **Windows:** Use WSL or download from https://github.com/microsoftarchive/redis/releases

### 3. **Configure for H2 and Redis**
- The `application-local.yml` files in both services are pre-configured
- No need for Postgres or Docker for local testing

### 4. **Build the Project**
```sh
./gradlew clean build
```

### 5. **Run Services**
Start each service in a separate terminal:

```sh
# Terminal 1 - User Service
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2 - Order Service
./gradlew :order-service:bootRun --args='--spring.profiles.active=local'
```

### 6. **Verify Services**
Check the logs for:
- Successful startup messages
- No error messages
- Health check status
- gRPC server initialization

### 7. **Access Tools**
- H2 Console:
  - User Service: http://localhost:8080/h2-console
  - Order Service: http://localhost:8081/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`, User: `sa`, Password: (leave blank)
- Actuator Endpoints:
  - Health: `/actuator/health`
  - Info: `/actuator/info`
  - Metrics: `/actuator/metrics`

---

## Testing the Services

### 1. gRPC Health Checks
```sh
# Check user-service health
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check

# Check order-service health
grpcurl -plaintext localhost:9091 grpc.health.v1.Health/Check
```

### 2. Create a User
```sh
grpcurl -plaintext -d '{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "firstName": "Alice",
  "lastName": "Smith"
}' localhost:9090 com.poc.grpc.user.UserService/CreateUser
```

### 3. Create an Order
```sh
# First, get a JWT token
grpcurl -plaintext -d '{
  "username": "alice",
  "password": "password123"
}' localhost:9090 com.poc.grpc.user.UserService/Login

# Then create an order using the token
grpcurl -plaintext \
  -H "authorization: Bearer <your-jwt-token>" \
  -d '{
    "userId": "<user-id>",
    "items": [
      {"productId": "123", "quantity": 2},
      {"productId": "456", "quantity": 1}
    ]
  }' \
  localhost:9091 com.poc.grpc.order.OrderService/CreateOrder
```

---

## Monitoring and Debugging

### Log Files
- **Local Environment:** Console output with colors
- **Dev/QA/Prod:** Log files in specified locations:
  - User Service: `/var/log/user-service/`
  - Order Service: `/var/log/order-service/`

### Log Levels
- **Local/Dev:** DEBUG level for detailed information
- **QA:** INFO level for general operation
- **Prod:** WARN level for important events

### Health Checks
Both services expose health endpoints:
- gRPC: `grpc.health.v1.Health/Check`
- HTTP: `/actuator/health`

### Metrics
Access metrics via actuator endpoints:
- `/actuator/metrics` - Various metrics
- `/actuator/prometheus` - Prometheus format

---

## Environment-Specific Configuration

### Local Development
- Console logging with colors
- H2 in-memory database
- Redis optional
- All actuator endpoints exposed

### Development (dev)
- File logging with rotation
- H2 or Postgres
- Redis required
- Limited actuator exposure

### Production
- Structured logging to files
- Postgres required
- Redis cluster
- Minimal actuator exposure
- Enhanced security

---

## Troubleshooting

### Common Issues
- **Redis not running?** Start it as shown above, or disable Redis beans in your config.
- **H2 errors?** Make sure the JDBC URL matches `jdbc:h2:mem:testdb`.
- **gRPC errors?** Double-check proto method names and request JSON.
- **JWT errors?** Ensure the secret is set and matches in your config.

### Logging
- Check appropriate log files based on environment
- Use correct log levels for debugging
- Look for correlation IDs in distributed calls

### Health Checks
- Use both gRPC and HTTP health checks
- Check individual component health
- Monitor Redis connectivity

---

## Contributing
Pull requests and suggestions are welcome! This project is designed for learning and experimentation.

---

## License
MIT (or your chosen license)
