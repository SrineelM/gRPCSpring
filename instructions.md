# Development Instructions and Guidelines

## Overview
This document provides comprehensive guidelines for developers working on the gRPC Spring microservices project.

---

## Table of Contents
1. [Getting Started](#getting-started)
2. [Project Structure](#project-structure)
3. [Development Workflow](#development-workflow)
4. [Code Standards](#code-standards)
5. [Testing Guidelines](#testing-guidelines)
6. [Security Best Practices](#security-best-practices)
7. [Performance Optimization](#performance-optimization)
8. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Prerequisites
- **Java 17+** (Java 21 recommended for virtual threads)
- **Gradle 7.6+**
- **Redis** (optional for local, required for dev/prod)
- **PostgreSQL** (for non-local profiles)
- **Docker & Docker Compose** (optional)
- **IntelliJ IDEA** or VS Code with Java extensions

### Initial Setup

#### 1. Clone and Build
```bash
git clone <repository-url>
cd gRPCSpring
./gradlew clean build
```

#### 2. Set Environment Variables
```bash
# Required for JWT
export JWT_SECRET="your-base64-encoded-secret-minimum-256-bits"

# Optional - Database (if not using H2)
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=userdb
export DB_USER=dbuser
export DB_PASSWORD=dbpassword

# Optional - Redis
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=
```

#### 3. Run Services Locally
```bash
# Terminal 1 - User Service
./gradlew :user-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2 - Order Service
./gradlew :order-service:bootRun --args='--spring.profiles.active=local'
```

#### 4. Verify Services
```bash
# Check health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health

# Test gRPC
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext localhost:9091 list
```

---

## Project Structure

```
gRPCSpring/
├── grpc-common/                    # Shared code across services
│   ├── src/main/
│   │   ├── java/com/poc/grpc/common/
│   │   │   ├── security/          # JWT, interceptors, authentication
│   │   │   ├── exception/         # Global exception handling
│   │   │   └── config/            # Shared configurations
│   │   └── proto/                 # Protocol buffer definitions
│   │       ├── user.proto
│   │       └── order.proto
│   └── build.gradle
│
├── user-service/                   # User management microservice
│   ├── src/main/
│   │   ├── java/com/poc/grpc/user/
│   │   │   ├── config/            # Service-specific configs
│   │   │   ├── entity/            # JPA entities
│   │   │   ├── repository/        # Data access layer
│   │   │   ├── service/           # gRPC service implementations
│   │   │   └── security/          # Auth components
│   │   └── resources/
│   │       ├── application.yml    # Base configuration
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── build.gradle
│
├── order-service/                  # Order management microservice
│   ├── src/main/
│   │   ├── java/com/poc/grpc/order/
│   │   │   ├── config/
│   │   │   ├── entity/
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   └── exception/
│   │   └── resources/
│   └── build.gradle
│
├── build.gradle                    # Root build configuration
├── settings.gradle                 # Multi-module settings
├── gradle.properties               # Dependency versions
├── README.md                       # Project documentation
├── testdata.md                     # Testing guide
├── instructions.md                 # This file
└── review.md                       # Architecture review
```

### Module Responsibilities

#### grpc-common
- **Purpose**: Shared code and contracts
- **Contains**:
  - Proto definitions (user.proto, order.proto)
  - JWT utilities and authentication
  - Security interceptors
  - Exception handling
  - Common configurations

#### user-service
- **Purpose**: User registration, authentication, profile management
- **Endpoints**:
  - CreateUser (public)
  - GetUser (authenticated)
  - UpdateUserProfile (authenticated)
  - ValidateUser (service-to-service)
  - HealthCheck (public)

#### order-service
- **Purpose**: Order creation and management
- **Endpoints**:
  - CreateOrder (authenticated)
  - GetOrder (authenticated)
  - ListUserOrders (authenticated)
  - UpdateOrderStatus (authenticated)
  - HealthCheck (public)

---

## Development Workflow

### 1. Feature Development

#### Create Feature Branch
```bash
git checkout -b feature/your-feature-name
```

#### Implement Changes
1. **Update Proto** (if needed):
   ```protobuf
   // grpc-common/src/main/proto/user.proto
   message NewRequest {
     string field1 = 1;
     string field2 = 2;
   }
   ```

2. **Regenerate gRPC Code**:
   ```bash
   ./gradlew :grpc-common:build
   ```

3. **Implement Service**:
   ```java
   @Override
   public void newMethod(NewRequest request, StreamObserver<Response> responseObserver) {
       // Implementation
   }
   ```

4. **Add Tests**:
   ```java
   @Test
   void testNewMethod() {
       // Test implementation
   }
   ```

#### Code Quality Checks
```bash
# Format code
./gradlew spotlessApply

# Run tests
./gradlew test

# Build all modules
./gradlew clean build
```

### 2. Testing Workflow

#### Unit Testing
```bash
# Run all unit tests
./gradlew test

# Run specific service tests
./gradlew :user-service:test
./gradlew :order-service:test

# Run with coverage
./gradlew jacocoTestReport
```

#### Integration Testing
```bash
# Run integration tests
./gradlew integrationTest

# With TestContainers
./gradlew :user-service:integrationTest
```

#### Manual Testing
See [testdata.md](testdata.md) for Postman testing guide.

### 3. Pull Request Workflow

#### Before Creating PR
1. ✅ All tests pass
2. ✅ Code formatted (spotlessApply)
3. ✅ No compilation errors
4. ✅ Documentation updated
5. ✅ CHANGELOG.md updated

#### PR Checklist
```markdown
## Changes
- [ ] Feature/bug description
- [ ] Related issue: #123

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

## Documentation
- [ ] README updated (if needed)
- [ ] API documentation updated
- [ ] Comments added to complex code

## Code Quality
- [ ] spotlessApply run
- [ ] No new warnings
- [ ] Performance impact considered
```

---

## Code Standards

### Java Code Style

#### 1. Google Java Format
Enforced via Spotless plugin:
```bash
./gradlew spotlessApply
```

#### 2. Naming Conventions
```java
// Classes: PascalCase
public class UserServiceImpl { }

// Methods: camelCase
public void createUser() { }

// Constants: UPPER_SNAKE_CASE
private static final String JWT_SECRET = "...";

// Variables: camelCase
private String userName;

// Packages: lowercase
package com.poc.grpc.user.service;
```

#### 3. Comments and JavaDoc

**Class Level:**
```java
/**
 * Service implementation for user management operations.
 * 
 * <p>This service handles:
 * <ul>
 *   <li>User registration and validation</li>
 *   <li>Profile management</li>
 *   <li>User authentication support</li>
 * </ul>
 * 
 * @author Development Team
 * @since 1.0.0
 */
@GrpcService
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {
    // Implementation
}
```

**Method Level:**
```java
/**
 * Creates a new user account with the provided details.
 * 
 * <p>This method:
 * <ul>
 *   <li>Validates username uniqueness</li>
 *   <li>Encrypts password using BCrypt</li>
 *   <li>Sends verification email</li>
 * </ul>
 * 
 * @param request the user creation request containing username, email, and password
 * @param responseObserver the response observer for sending user data
 * @throws StatusRuntimeException if username already exists or validation fails
 */
@Override
public void createUser(CreateUserRequest request, StreamObserver<UserResponse> responseObserver) {
    // Implementation
}
```

**Inline Comments:**
```java
// Extract JWT from authorization header
String authHeader = headers.get(AUTHORIZATION_HEADER_KEY);

// Validate token cryptographically before database lookup
if (!jwtUtil.validateToken(jwt)) {
    throw new SecurityException("Invalid JWT token");
}

// Load user details from database to ensure current status
UserDetails userDetails = userDetailsService.loadUserByUsername(username);
```

#### 4. Lombok Usage
```java
@Slf4j                    // Logging
@RequiredArgsConstructor  // Constructor injection
@Builder                  // Builder pattern
@Data                     // Getters, setters, toString, equals, hashCode
@Entity                   // JPA entity
public class User {
    // Fields
}
```

### Proto File Standards

```protobuf
/**
 * Proto definitions for the User Service
 * 
 * This file defines user management operations including:
 * - User registration and authentication
 * - Profile management
 * - User validation
 */

syntax = "proto3";

package com.poc.grpc.user;

option java_multiple_files = true;
option java_package = "com.poc.grpc.user";
option java_outer_classname = "UserProto";

// Service definition with clear method descriptions
service UserService {
    // Creates a new user account
    rpc CreateUser (CreateUserRequest) returns (UserResponse);
    
    // Retrieves user details by ID
    rpc GetUser (GetUserRequest) returns (UserResponse);
}

// Request messages with field descriptions
message CreateUserRequest {
    string username = 1;        // Unique username (required)
    string email = 2;          // Valid email address (required)
    string password = 3;       // Minimum 8 characters (required)
    string first_name = 4;     // User's first name (optional)
    string last_name = 5;      // User's last name (optional)
}
```

### Configuration Standards

```yaml
# Application Configuration Best Practices

# 1. Use environment variables for secrets
app:
  jwt:
    secret: ${JWT_SECRET:}  # No default for secrets
    expiration: 86400000    # Explicit values for clarity

# 2. Document all properties
grpc:
  server:
    port: 9090              # gRPC server port
    enable-reflection: true # Enable for development, disable for production

# 3. Profile-specific overrides
---
spring:
  config:
    activate:
      on-profile: prod
      
# 4. Meaningful defaults
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50  # 50% failure rate triggers circuit
```

---

## Testing Guidelines

### Unit Testing

#### Test Structure
```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @InjectMocks
    private UserServiceImpl userService;
    
    @Test
    @DisplayName("Should create user successfully when valid request provided")
    void shouldCreateUserSuccessfully() {
        // Given
        CreateUserRequest request = CreateUserRequest.newBuilder()
            .setUsername("testuser")
            .setEmail("test@example.com")
            .setPassword("Password123!")
            .build();
        
        StreamObserver<UserResponse> responseObserver = mock(StreamObserver.class);
        
        // When
        userService.createUser(request, responseObserver);
        
        // Then
        verify(responseObserver).onNext(any(UserResponse.class));
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }
    
    @Test
    @DisplayName("Should throw ALREADY_EXISTS when username is duplicate")
    void shouldThrowWhenUsernameExists() {
        // Given - user already exists
        when(userRepository.existsByUsername("existing"))
            .thenReturn(true);
        
        // When/Then
        StatusRuntimeException exception = assertThrows(
            StatusRuntimeException.class,
            () -> userService.createUser(request, responseObserver)
        );
        
        assertEquals(Status.Code.ALREADY_EXISTS, exception.getStatus().getCode());
    }
}
```

### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.profiles.active=test",
    "grpc.server.port=0"  // Random port
})
class UserServiceIntegrationTest {
    
    @Autowired
    private GrpcChannelFactory channelFactory;
    
    private UserServiceGrpc.UserServiceBlockingStub stub;
    
    @BeforeEach
    void setup() {
        ManagedChannel channel = channelFactory.createChannel("user-service");
        stub = UserServiceGrpc.newBlockingStub(channel);
    }
    
    @Test
    void shouldCreateAndRetrieveUser() {
        // Create user
        CreateUserRequest createRequest = CreateUserRequest.newBuilder()
            .setUsername("integrationtest")
            .setEmail("integration@test.com")
            .setPassword("Test@123")
            .build();
        
        UserResponse createResponse = stub.createUser(createRequest);
        assertNotNull(createResponse.getUserId());
        
        // Retrieve user
        GetUserRequest getRequest = GetUserRequest.newBuilder()
            .setUserId(createResponse.getUserId())
            .build();
        
        UserResponse getResponse = stub.getUser(getRequest);
        assertEquals("integrationtest", getResponse.getUsername());
    }
}
```

### Test Coverage Goals
- **Minimum**: 80% line coverage
- **Target**: 90% line coverage for business logic
- **100% coverage**: Security-critical code (JWT, authentication)

---

## Security Best Practices

### 1. JWT Handling

**DO:**
```java
// Use environment variables for secrets
@Value("${app.jwt.secret}")
private String jwtSecret;

// Validate tokens thoroughly
public boolean validateToken(String token) {
    try {
        parseToken(token);
        return true;
    } catch (ExpiredJwtException e) {
        log.error("Expired token");
        return false;
    } catch (Exception e) {
        log.error("Invalid token");
        return false;
    }
}

// Clear security context after use
finally {
    SecurityContextHolder.clearContext();
}
```

**DON'T:**
```java
// Never hardcode secrets
private String secret = "my-secret-key";  // ❌ WRONG

// Never log tokens
log.info("Token: {}", jwtToken);  // ❌ WRONG

// Never skip validation
SecurityContextHolder.getContext().setAuthentication(auth);  // ❌ Without validation
```

### 2. Input Validation

```java
// Validate all inputs
if (request.getUsername().isEmpty()) {
    throw Status.INVALID_ARGUMENT
        .withDescription("Username cannot be empty")
        .asRuntimeException();
}

// Use regex for format validation
private static final Pattern EMAIL_PATTERN = 
    Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

if (!EMAIL_PATTERN.matcher(email).matches()) {
    throw Status.INVALID_ARGUMENT
        .withDescription("Invalid email format")
        .asRuntimeException();
}
```

### 3. Password Security

```java
// Always use BCrypt or similar
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);  // Strength 10
}

// Never store plain text passwords
String hashedPassword = passwordEncoder.encode(plainPassword);
user.setPassword(hashedPassword);  // ✅ CORRECT
```

### 4. Audit Logging

```java
// Log security events
log.info("User authentication successful: {}", username);
log.warn("Failed login attempt for user: {}", username);
log.error("Unauthorized access attempt to resource: {}", resourceId);

// Include correlation IDs
MDC.put("correlationId", correlationId);
log.info("Processing request");
MDC.clear();
```

---

## Performance Optimization

### 1. JVM Configuration for 8GB RAM

```bash
# Recommended JVM flags
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
```

### 2. Connection Pool Tuning

```yaml
# HikariCP for 8GB RAM
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # Reduced from default 20
      minimum-idle: 3            # Minimum connections
      connection-timeout: 20000  # 20 seconds
      idle-timeout: 300000       # 5 minutes
      max-lifetime: 1200000      # 20 minutes
```

### 3. gRPC Optimization

```yaml
grpc:
  server:
    max-inbound-message-size: 4MB   # Limit message size
    keep-alive-time: 30s            # Connection keep-alive
    keep-alive-timeout: 10s
    executor:
      core-pool-size: 10
      max-pool-size: 20
      queue-capacity: 100
```

### 4. Caching Strategy

```java
@Cacheable(value = "users", key = "#userId")
public User getUserById(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
}

@CacheEvict(value = "users", key = "#userId")
public void updateUser(String userId, User user) {
    userRepository.save(user);
}
```

### 5. Async Processing

```java
@Async
public CompletableFuture<Void> sendEmailAsync(String email, String subject, String body) {
    // Long-running email operation
    emailService.send(email, subject, body);
    return CompletableFuture.completedFuture(null);
}
```

---

## Troubleshooting

### Common Issues

#### 1. Port Already in Use
```bash
# Find process using port
lsof -ti:9090 | xargs kill -9

# Or change port in application.yml
grpc:
  server:
    port: 9092
```

#### 2. JWT Validation Fails
```bash
# Ensure JWT_SECRET is set
echo $JWT_SECRET

# Must be Base64 encoded and 256+ bits
export JWT_SECRET=$(openssl rand -base64 32)
```

#### 3. Proto Compilation Errors
```bash
# Clean and rebuild
./gradlew :grpc-common:clean
./gradlew :grpc-common:build

# Check generated files
ls -la grpc-common/build/generated/source/proto/main/
```

#### 4. Database Connection Issues
```yaml
# For H2 local testing
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password:

# Check H2 console: http://localhost:8080/h2-console
```

#### 5. Redis Connection Fails
```bash
# Start Redis
brew services start redis  # macOS
sudo service redis-server start  # Linux

# Test connection
redis-cli ping  # Should return PONG
```

### Debugging Tips

#### Enable Debug Logging
```yaml
logging:
  level:
    com.poc.grpc: DEBUG
    io.grpc: DEBUG
    org.springframework.security: DEBUG
```

#### Monitor Memory Usage
```bash
# Check JVM memory
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Generate heap dump
jmap -dump:live,format=b,file=heap.bin <pid>
```

#### Trace gRPC Calls
```bash
# Enable gRPC logging
export GRPC_TRACE=all
export GRPC_VERBOSITY=DEBUG
```

---

## Additional Resources

### Official Documentation
- [gRPC Java](https://grpc.io/docs/languages/java/)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [gRPC Spring Boot Starter](https://yidongnan.github.io/grpc-spring-boot-starter/)
- [Resilience4j](https://resilience4j.readme.io/)

### Development Tools
- [BloomRPC](https://github.com/bloomrpc/bloomrpc) - gRPC GUI client
- [grpcurl](https://github.com/fullstorydev/grpcurl) - CLI for gRPC
- [Postman](https://www.postman.com/) - API testing (gRPC support)

### Learning Resources
- [Building Microservices by Sam Newman](https://www.oreilly.com/library/view/building-microservices-2nd/9781492034018/)
- [gRPC: Up and Running](https://www.oreilly.com/library/view/grpc-up-and/9781492058328/)

---

*Last Updated: November 1, 2025*
*Version: 1.0.0*
