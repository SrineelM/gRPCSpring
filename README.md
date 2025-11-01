# gRPCSpring Microservices - Production-Ready Reference Implementation

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.2-green.svg)](https://spring.io/projects/spring-boot)
[![gRPC](https://img.shields.io/badge/gRPC-1.65.1-orange.svg)](https://grpc.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

This project demonstrates a **production-ready microservices architecture** using **Spring Boot 3.x** and **gRPC**. Designed as a comprehensive reference implementation for architects and developers learning modern distributed systems (2023-2025).

**Key Differentiators:**
- ✅ **Complete observability** with correlation ID tracking and structured logging
- ✅ **Production-grade security** with JWT authentication and audit logging
- ✅ **Fault tolerance** using Resilience4j (circuit breaker, retry, bulkhead)
- ✅ **Optimized for 8GB RAM** development environments
- ✅ **Comprehensive documentation** with Postman testing guides
- ✅ **Educational focus** with detailed code comments and architecture explanations

The system consists of two main microservices:
- **user-service**: User registration, authentication, and profile management
- **order-service**: Order creation, management, and history tracking
- **grpc-common**: Shared proto definitions, security, and interceptors

All services communicate using **gRPC** for high-performance, strongly-typed RPC with full observability.

---

## Features

### Core Microservices Features
- ✅ **Spring Boot 3.3.2** with Jakarta EE for modern Java development
- ✅ **gRPC 1.65.1** for fast, contract-based inter-service communication
- ✅ **JWT Authentication** with RS512 signing and role-based access control
- ✅ **Multi-profile support** (local/dev/qa/staging/prod)
- ✅ **H2 in-memory database** for zero-config local development
- ✅ **PostgreSQL** support for production environments
- ✅ **Redis caching** for performance optimization

### Security & Authentication
- ✅ **JWT-based stateless authentication** with token validation
- ✅ **Spring Security 6.x** integration
- ✅ **BCrypt password encryption** (strength 10)
- ✅ **Method-level authorization** with @PreAuthorize
- ✅ **Secure gRPC interceptors** with proper context cleanup
- ✅ **Audit logging** for security events
- ✅ **Token expiration** and validation

### Observability & Monitoring
- ✅ **Correlation ID tracking** across all microservices
- ✅ **Structured logging** with MDC (Mapped Diagnostic Context)
- ✅ **Spring Boot Actuator** for health checks and metrics
- ✅ **Prometheus metrics** export
- ✅ **Custom business metrics** integration
- ✅ **Distributed tracing ready** (Zipkin/Jaeger compatible)
- ✅ **gRPC health check protocol** implementation

### Fault Tolerance & Resiliency
- ✅ **Circuit Breaker** pattern with Resilience4j
- ✅ **Retry mechanism** with exponential backoff
- ✅ **Bulkhead** for resource isolation
- ✅ **Time limiter** for timeout management
- ✅ **Graceful degradation** with fallback methods
- ✅ **Connection keep-alive** and health monitoring

### Performance Optimization
- ✅ **Optimized for 8GB RAM** environments
- ✅ **HikariCP connection pooling** with tuned settings
- ✅ **Caffeine caching** for frequently accessed data
- ✅ **gRPC keep-alive** configuration
- ✅ **Efficient thread pool** management
- ✅ **JVM tuning recommendations** included

### Development & Testing
- ✅ **Google Java Format** enforcement via Spotless
- ✅ **Lombok** for boilerplate reduction
- ✅ **Comprehensive JavaDoc** comments
- ✅ **Postman testing guide** with examples
- ✅ **grpcurl command examples**
- ✅ **H2 Console** for database inspection
- ✅ **Docker Compose** for infrastructure

### Documentation
- ✅ **Detailed README** with architecture overview
- ✅ **testdata.md** - Complete Postman testing guide
- ✅ **instructions.md** - Development guidelines
- ✅ **review.md** - Architecture review and best practices
- ✅ **Inline code comments** explaining complex logic
- ✅ **Proto file documentation**

---

## Architecture

### High-Level Architecture

```
┌─────────────────┐         gRPC (9090)          ┌─────────────────┐
│                 │◄────────────────────────────►│                 │
│  user-service   │                               │  order-service  │
│                 │      Correlation IDs          │                 │
│  - Registration │      JWT Auth                 │  - Create Order │
│  - Authentication│     Observability            │  - Get Order    │
│  - Profile Mgmt │                               │  - List Orders  │
│                 │                               │                 │
└────────┬────────┘                               └────────┬────────┘
         │                                                 │
         │                                                 │
    ┌────▼────┐                                      ┌────▼────┐
    │  H2 DB  │                                      │  H2 DB  │
    │ (local) │                                      │ (local) │
    └─────────┘                                      └─────────┘
         │                                                 │
         │              ┌──────────────┐                  │
         └──────────────►  Redis Cache ◄──────────────────┘
                        │  (Shared)    │
                        └──────────────┘
                                │
                                ▼
                    ┌────────────────────────┐
                    │   Monitoring Stack     │
                    │  - Prometheus          │
                    │  - Grafana             │
                    │  - Zipkin (Tracing)    │
                    └────────────────────────┘
```

### Request Flow with Correlation ID

```
Client Request
     │
     ├─► [CorrelationIdInterceptor] Generate/Extract correlation ID
     │                              Add to MDC and gRPC Context
     │
     ├─► [GrpcAuthInterceptor]     Extract & validate JWT
     │                              Load user details
     │                              Set SecurityContext
     │
     ├─► [Service Method]           Execute business logic
     │                              Access correlation ID from context
     │                              Log with correlation ID
     │
     └─► [Response]                 Include correlation ID in response
                                    Clean up MDC and SecurityContext
```

### Component Interactions

```
┌──────────────────────────────────────────────────────────┐
│                     grpc-common                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Proto Definitions (user.proto, order.proto)      │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Security (JwtUtil, JwtAuthenticator)             │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Interceptors (CorrelationId, Logging)            │ │
│  └────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Exception Handling (GlobalExceptionHandler)      │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
                            ▲
                            │ (Shared Dependency)
            ┌───────────────┴───────────────┐
            │                               │
┌───────────▼────────────┐      ┌──────────▼────────────┐
│   user-service         │      │   order-service       │
│                        │      │                       │
│  - UserServiceImpl     │      │  - OrderServiceImpl   │
│  - UserRepository      │      │  - OrderRepository    │
│  - CustomUserDetails   │      │  - User Validation    │
│  - GrpcAuthInterceptor │      │  - JWT Validation     │
└────────────────────────┘      └───────────────────────┘
```

---

## Tech Stack (2023-2025 Standards)

### Core Framework
| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17+ (21 recommended) | Programming language with modern features |
| Spring Boot | 3.3.2 | Application framework with Jakarta EE |
| Spring Security | 6.x | Authentication and authorization |
| gRPC Java | 1.65.1 | RPC framework for service communication |
| Protocol Buffers | 3.25.5 | Service contract definition |

### Data & Caching
| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Data JPA | 3.x | Data access layer |
| H2 Database | Latest | In-memory database (local/dev) |
| PostgreSQL | 14+ | Production database |
| Redis | 7.x | Distributed caching and sessions |
| HikariCP | Latest | JDBC connection pooling |

### Security
| Component | Version | Purpose |
|-----------|---------|---------|
| JWT (jjwt) | 0.12.5 | JSON Web Token implementation |
| BCrypt | Built-in | Password hashing algorithm |
| Spring Security | 6.x | Security framework |

### Resilience & Fault Tolerance
| Component | Version | Purpose |
|-----------|---------|---------|
| Resilience4j | 2.2.0 | Circuit breaker, retry, bulkhead |
| Spring Retry | Built-in | Retry mechanism |

### Observability
| Component | Version | Purpose |
|-----------|---------|---------|
| Spring Boot Actuator | 3.x | Health checks and metrics |
| Micrometer | Latest | Metrics instrumentation |
| SLF4J + Logback | 2.0.12 / 1.4.14 | Logging framework |
| Prometheus | Compatible | Metrics collection |
| Zipkin (Optional) | Latest | Distributed tracing |

### Development Tools
| Tool | Version | Purpose |
|------|---------|---------|
| Gradle | 7.6+ | Build automation |
| Lombok | 1.18.34 | Boilerplate code reduction |
| Spotless | 6.25.0 | Code formatting (Google Java Format) |
| Docker | Latest | Containerization |
| Docker Compose | Latest | Multi-container orchestration |

### Testing (Recommended)
| Tool | Purpose |
|------|---------|
| JUnit 5 | Unit testing framework |
| Mockito | Mocking framework |
| TestContainers | Integration testing with containers |
| grpcurl | gRPC command-line testing |
| Postman | gRPC GUI testing |

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

### Quick Start Testing

For complete testing guide with Postman examples, see **[testdata.md](testdata.md)**.

### 1. Health Checks

```bash
# gRPC Health Checks
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
grpcurl -plaintext localhost:9091 grpc.health.v1.Health/Check

# HTTP Actuator Health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
```

### 2. User Registration & Authentication

```bash
# Create a new user
grpcurl -plaintext -d '{
  "username": "alice",
  "email": "alice@example.com",
  "password": "Alice@123",
  "firstName": "Alice",
  "lastName": "Johnson"
}' localhost:9090 com.poc.grpc.user.UserService/CreateUser

# Response includes userId - save it for later use
```

### 3. Get User (Requires Auth)

```bash
# First, authenticate to get JWT token (if login endpoint exists)
# Then use the token in the authorization header
grpcurl -plaintext \
  -H "authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{"userId": "user-id-from-create"}' \
  localhost:9090 com.poc.grpc.user.UserService/GetUser
```

### 4. Create Order (Requires Auth)

```bash
grpcurl -plaintext \
  -H "authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": "your-user-id",
    "items": [
      {
        "productId": "PROD-001",
        "name": "Laptop",
        "quantity": 1,
        "price": 999.99
      }
    ],
    "shippingAddress": "123 Main St, San Francisco, CA",
    "paymentMethod": "CREDIT_CARD"
  }' \
  localhost:9091 com.poc.grpc.order.OrderService/CreateOrder
```

### 5. Postman Testing

See **[testdata.md](testdata.md)** for:
- Complete Postman setup instructions
- All service endpoints with examples
- Error scenario testing
- Bulk testing data
- Database verification queries

---

## Performance Optimization for 8GB RAM

This project is optimized to run efficiently on systems with 8GB RAM.

### JVM Configuration

Add these JVM options when running services:

```bash
# Recommended JVM flags for 8GB RAM system
export JAVA_OPTS="\
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:ParallelGCThreads=4 \
  -XX:ConcGCThreads=2 \
  -XX:+UseStringDeduplication \
  -XX:MaxMetaspaceSize=512m \
  -Xss512k \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heap-dump.hprof"

# Run with optimized settings
./gradlew :user-service:bootRun
```

### Connection Pool Tuning

The project includes optimized connection pool settings:

```yaml
# HikariCP - Optimized for 8GB RAM
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # Reduced from default 20
      minimum-idle: 3            # Minimum active connections
      connection-timeout: 20000  # 20 seconds
```

### gRPC Configuration

```yaml
grpc:
  server:
    max-inbound-message-size: 4MB  # Limit message size
    executor:
      core-pool-size: 10           # Core threads
      max-pool-size: 20            # Maximum threads
      queue-capacity: 100          # Request queue size
```

### Monitoring Resource Usage

```bash
# Check JVM memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max

# Check thread pools
curl http://localhost:8080/actuator/metrics/executor.active
curl http://localhost:8080/actuator/metrics/executor.pool.size

# Check database connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### Performance Tips

1. **Limit Concurrent Requests**: Use Resilience4j bulkhead pattern
2. **Enable Caching**: Redis caching for frequently accessed data
3. **Connection Pooling**: HikariCP with optimized pool sizes
4. **Async Processing**: Use @Async for non-blocking operations
5. **Circuit Breakers**: Prevent cascade failures

---

## Observability & Monitoring

### Correlation ID Tracking

Every request is assigned a unique correlation ID for distributed tracing:

```java
// Automatic correlation ID in logs
2025-11-01 10:00:00 [correlationId=abc-123-def-456] INFO  Creating user: alice
2025-11-01 10:00:01 [correlationId=abc-123-def-456] INFO  User created successfully
```

### Metrics Collection

```bash
# Prometheus metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Specific metrics
curl http://localhost:8080/actuator/metrics/grpc.server.calls
curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
```

### Health Indicators

```bash
# Detailed health check
curl http://localhost:8080/actuator/health

# Response includes:
# - Application status
# - Database connectivity
# - Redis connectivity
# - Disk space
# - Custom health indicators
```

### Logging

Structured logging with multiple levels:
- **Local**: Console output with correlation IDs
- **Dev/QA**: File-based with rotation
- **Production**: JSON format for log aggregation

```yaml
logging:
  level:
    com.poc.grpc: DEBUG
    io.grpc: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n"
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

## Quick Build, Run & Test Guide

### Prerequisites
- Java 17+
- Redis (optional for local development)

### Build
```bash
./gradlew clean build
```

### Run Services
```bash
# Terminal 1 - User Service
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2 - Order Service  
./gradlew :order-service:bootRun --args='--spring.profiles.active=local'
```

### Test Services
```bash
# Health checks
grpcurl -plaintext localhost:9090 grpc.health.v1.Health/Check
grpcurl -plaintext localhost:9091 grpc.health.v1.Health/Check

# Create user (see testdata.md for complete examples)
grpcurl -plaintext -d '{"username": "alice", "email": "alice@example.com", "password": "Alice@123", "firstName": "Alice", "lastName": "Johnson"}' localhost:9090 com.poc.grpc.user.UserService/CreateUser

# Access H2 Console
# User Service: http://localhost:8080/h2-console
# Order Service: http://localhost:8081/h2-console
# JDBC URL: jdbc:h2:mem:testdb, User: sa, Password: (blank)
```

For detailed testing scenarios, see **[testdata.md](testdata.md)**.

---

## Contributing
Pull requests and suggestions are welcome! This project is designed for learning and experimentation.

---

## License
MIT (or your chosen license)
