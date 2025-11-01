package com.poc.grpc.common.config;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration
 *
 * <p>This configuration class sets up Redis caching infrastructure. It provides caching
 * capabilities for improved performance.
 *
 * <p>Features: 1. Redis connection setup 2. Cache manager configuration 3. JSON serialization 4.
 * TTL management
 *
 * <p>Cache Regions: - users: User data (30 min TTL) - orders: Order data (1 hour TTL) - validation:
 * User validation results (15 min TTL)
 *
 * <p>Environment Settings: - Local: Localhost Redis - Dev/QA: Dedicated Redis - Prod: Redis cluster
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

  // Injected RedisProperties to access connection details from application.yml.
  private final RedisProperties redisProperties;

  /**
   * Configures the Redis connection factory, which establishes the connection to the Redis server.
   * This implementation uses Lettuce, a high-performance, scalable, and non-blocking Redis client.
   *
   * @return The configured connection factory
   */
  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    log.info("Configuring Redis connection factory");
    log.debug(
        "Redis connection properties - Host: {}, Port: {}",
        redisProperties.getHost(),
        redisProperties.getPort());

    // LettuceConnectionFactory is the standard choice in modern Spring applications.
    LettuceConnectionFactory factory = new LettuceConnectionFactory();
    factory.setHostName(redisProperties.getHost());
    factory.setPort(redisProperties.getPort());
    factory.setDatabase(redisProperties.getDatabase());
    factory.setPassword(redisProperties.getPassword());

    return factory;
  }

  /**
   * Configures the RedisTemplate, a high-level abstraction for Redis interactions. It simplifies
   * data access by handling serialization and connection management, allowing developers to focus
   * on business logic.
   *
   * @param connectionFactory The Redis connection factory
   * @return The configured Redis template
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    log.info("Configuring Redis template");

    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Use StringRedisSerializer for keys to ensure they are human-readable in Redis CLI.
    template.setKeySerializer(new StringRedisSerializer());
    // Use GenericJackson2JsonRedisSerializer for values to store complex objects as JSON.
    // This makes the cache content language-agnostic and easier to debug.
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    // Also use JSON serialization for hash values.
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

    log.debug("Redis template configured with JSON serialization");
    return template;
  }

  /**
   * Configures the CacheManager, which is the central component for Spring's caching abstraction
   * (@Cacheable, @CachePut, etc.). This bean defines default caching behavior and allows for
   * per-cache customizations (e.g., different TTLs for different data types).
   *
   * @param connectionFactory The Redis connection factory
   * @return The configured cache manager
   */
  @Bean
  public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    log.info("Configuring Redis cache manager");

    // Define a default cache configuration that will be applied to any cache that doesn't have a
    // specific configuration.
    RedisCacheConfiguration defaultConfig =
        RedisCacheConfiguration.defaultCacheConfig()
            // Set a default Time-To-Live (TTL) of 30 minutes for cache entries.
            .entryTtl(Duration.ofMinutes(30))
            // Configure key serialization to use strings.
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
            // Configure value serialization to use JSON, consistent with the RedisTemplate.
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()));

    // Create a map of specific configurations for named caches, overriding the default TTL.
    // This allows for fine-grained control over caching behavior for different parts of the
    // application.
    var configMap =
        Map.of(
            "users", defaultConfig.entryTtl(Duration.ofMinutes(30)), // Cache for user data
            "orders", defaultConfig.entryTtl(Duration.ofHours(1)), // Longer cache for order data
            "validation",
                defaultConfig.entryTtl(
                    Duration.ofMinutes(15)) // Shorter cache for validation tokens
            );

    log.debug("Cache regions configured with TTLs - users: 30m, orders: 1h, validation: 15m");

    // Build the RedisCacheManager using the default and specific configurations.
    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig)
        .withInitialCacheConfigurations(configMap)
        .build();
  }
}
