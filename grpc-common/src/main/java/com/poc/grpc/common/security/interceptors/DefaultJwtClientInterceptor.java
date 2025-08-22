package com.poc.grpc.common.security.interceptors;

import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

/**
 * Default implementation of JWT client interceptor with enhanced capabilities:
 *
 * <ul>
 *   <li>Token caching to reduce the overhead of token generation
 *   <li>Retry mechanism for token retrieval failures
 *   <li>Token expiration management
 * </ul>
 */
@Slf4j
public class DefaultJwtClientInterceptor extends AbstractJwtClientInterceptor {

  // Token cache: user ID -> CachedToken
  private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

  // Scheduler for cache cleanup
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  // Maximum retry attempts
  private static final int MAX_RETRY_ATTEMPTS = 3;

  // Delay between retry attempts in milliseconds
  private static final long RETRY_DELAY_MS = 100;

  /**
   * Creates a new DefaultJwtClientInterceptor.
   *
   * @param jwtUtil JWT utility for token operations
   * @param securityProperties Security configuration properties
   */
  public DefaultJwtClientInterceptor(
      JwtUtil jwtUtil, SecurityConfigurationProperties securityProperties) {
    super(jwtUtil, securityProperties);

    // Schedule cache cleanup
    long cleanupIntervalMs =
        securityProperties.getJwt().getExpirationMs() / 10; // 10% of token expiration
    scheduler.scheduleAtFixedRate(
        this::cleanupExpiredTokens, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS);

    // Add shutdown hook to clean up scheduler
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    log.info("Shutting down token cache scheduler");
                    scheduler.shutdownNow();
                  } catch (Exception e) {
                    log.error("Error shutting down token cache scheduler", e);
                  }
                }));
  }

  @Override
  protected String customGenerateToken(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    String userId = authentication.getName();

    // Try to get token from cache first
    CachedToken cachedToken = tokenCache.get(userId);
    if (cachedToken != null && !cachedToken.isExpired()) {
      log.debug("Using cached JWT token for user: {}", userId);
      return cachedToken.getToken();
    }

    // Generate new token with retry mechanism
    for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        log.debug(
            "Generating new JWT token for user: {} (attempt {}/{})",
            userId,
            attempt + 1,
            MAX_RETRY_ATTEMPTS);

        String token = jwtUtil.generateToken(authentication);
        if (token != null) {
          // Calculate expiration time (90% of configured expiration to account for clock skew)
          long expirationMs = (long) (securityProperties.getJwt().getExpirationMs() * 0.9);
          long expirationTime = System.currentTimeMillis() + expirationMs;

          // Cache the generated token
          CachedToken newCachedToken = new CachedToken(token, expirationTime);
          tokenCache.put(userId, newCachedToken);

          log.debug(
              "JWT token generated and cached for user: {}, expires in {}ms", userId, expirationMs);
          return token;
        }

        // If token generation returned null, retry after delay
        if (attempt < MAX_RETRY_ATTEMPTS - 1) {
          Thread.sleep(RETRY_DELAY_MS);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to generate JWT token (attempt {}/{}): {}",
            attempt + 1,
            MAX_RETRY_ATTEMPTS,
            e.getMessage());

        // If this is the last attempt, log the full exception
        if (attempt == MAX_RETRY_ATTEMPTS - 1) {
          log.error("Failed to generate JWT token after {} attempts", MAX_RETRY_ATTEMPTS, e);
        } else {
          try {
            Thread.sleep(RETRY_DELAY_MS);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Retry delay interrupted", ie);
          }
        }
      }
    }

    return null;
  }

  @Override
  protected SecurityLevel getSecurityLevel() {
    // Override to provide custom logic if needed
    return super.getSecurityLevel();
  }

  /**
   * Clears the token cache for a specific user.
   *
   * @param userId The user ID
   */
  public void clearTokenCache(String userId) {
    tokenCache.remove(userId);
    log.debug("Cleared JWT token cache for user: {}", userId);
  }

  /** Clears the entire token cache. */
  public void clearAllTokenCache() {
    tokenCache.clear();
    log.debug("Cleared all JWT token caches");
  }

  /**
   * Gets the current size of the token cache.
   *
   * @return The number of entries in the token cache
   */
  public int getTokenCacheSize() {
    return tokenCache.size();
  }

  /** Removes expired tokens from the cache. */
  private void cleanupExpiredTokens() {
    try {
      int sizeBefore = tokenCache.size();
      tokenCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
      int sizeAfter = tokenCache.size();

      if (sizeBefore > sizeAfter) {
        log.debug("Cleaned up {} expired tokens from cache", sizeBefore - sizeAfter);
      }
    } catch (Exception e) {
      log.error("Error cleaning up expired tokens", e);
    }
  }

  /** Class representing a cached JWT token with expiration time. */
  private static class CachedToken {
    private final String token;
    private final long expirationTime;

    /**
     * Creates a new CachedToken.
     *
     * @param token The JWT token
     * @param expirationTime The expiration time in milliseconds since epoch
     */
    CachedToken(String token, long expirationTime) {
      this.token = token;
      this.expirationTime = expirationTime;
    }

    /**
     * Gets the JWT token.
     *
     * @return The JWT token
     */
    String getToken() {
      return token;
    }

    /**
     * Checks if the token is expired.
     *
     * @return true if the token is expired, false otherwise
     */
    boolean isExpired() {
      return System.currentTimeMillis() >= expirationTime;
    }
  }
}
