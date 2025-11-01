# Order Service Dockerfile
#
# This Dockerfile creates a production-ready container for the order service.
# It uses a multi-stage build to minimize the final image size and includes
# security best practices.
#
# Build Stages:
# 1. Build: Compiles the application using Gradle
# 2. Runtime: Creates the final image with JRE and application
#
# Features:
# - Multi-stage build
# - Security hardening
# - Health checks
# - Resource limits
# - Non-root user

# Stage 1: Build the application
FROM gradle:8.5-jdk17 AS builder

# Set working directory
WORKDIR /app

# Copy Gradle files first for better caching
COPY settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradle.properties ./

# Copy source code
COPY grpc-common ./grpc-common
COPY order-service ./order-service

# Build the application
RUN gradle :order-service:build --no-daemon

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine

# Add metadata labels
LABEL maintainer="Your Name <your.email@example.com>"
LABEL version="1.0.0"
LABEL description="Order Service for gRPC Spring Microservices Demo"

# Set environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"
ENV APP_HOME="/app"
ENV SPRING_PROFILES_ACTIVE="docker"

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set working directory
WORKDIR $APP_HOME

# Copy application JAR from builder stage
COPY --from=builder /app/order-service/build/libs/*.jar app.jar

# Install curl for health check
RUN apk add --no-cache curl

# Set ownership and permissions
RUN chown -R appuser:appgroup $APP_HOME && \
    chmod -R 755 $APP_HOME

# Switch to non-root user
USER appuser

# Expose ports
EXPOSE 8081 9091

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]