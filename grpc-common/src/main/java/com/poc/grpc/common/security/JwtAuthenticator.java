package com.poc.grpc.common.security;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * A component responsible for authenticating a user based on a JWT string.
 *
 * <p>This class acts as a high-level orchestrator, bridging the gap between low-level token parsing
 * and the application's user data model. It combines the cryptographic validation from {@link
 * JwtUtil} with user retrieval from Spring Security's {@link UserDetailsService} to produce a
 * fully-formed {@code Authentication} object.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Caching of UserDetails to reduce database load
 *   <li>Periodic cache cleanup to prevent memory leaks
 *   <li>Comprehensive error handling and logging
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jwt.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(UserDetailsService.class)
public class JwtAuthenticator {

  private final JwtUtil jwtUtil;
  // Injects the standard Spring Security service for loading user-specific data.
  // This decouples JWT logic from how user details (like roles and permissions) are stored.
  private final UserDetailsService userDetailsService;

  // Cache configuration properties
  @Value("${app.security.user-cache.enabled:true}")
  private boolean cacheEnabled;

  @Value("${app.security.user-cache.expiration-ms:300000}")
  private long cacheExpirationMs;

  @Value("${app.security.user-cache.cleanup-interval-ms:600000}")
  private long cacheCleanupIntervalMs;

  // Cache implementation
  private final ConcurrentHashMap<String, CachedUserDetails> userDetailsCache =
      new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  /** Initialize the cache cleanup scheduler. */
  @PostConstruct
  public void init() {
    if (cacheEnabled) {
      log.info("User details cache enabled with expiration of {}ms", cacheExpirationMs);
      scheduler.scheduleAtFixedRate(
          this::cleanupCache,
          cacheCleanupIntervalMs,
          cacheCleanupIntervalMs,
          TimeUnit.MILLISECONDS);
      log.info("Scheduled user details cache cleanup every {}ms", cacheCleanupIntervalMs);
    } else {
      log.info("User details cache disabled");
    }
  }

  /** Shutdown the scheduler on application shutdown. */
  @PreDestroy
  public void shutdown() {
    if (!scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Validates a JWT and constructs a Spring Security Authentication object if the token is valid
   * and corresponds to an existing user.
   *
   * @param jwt The raw JWT string extracted from the Authorization header.
   * @return An {@link Optional} containing the fully populated {@code Authentication} object if
   *     successful, otherwise an empty {@code Optional}.
   */
  public Optional<Authentication> getAuthentication(String jwt) {
    // Step 1: Perform cryptographic validation on the token.
    // This checks the signature, expiration, and format without hitting the database.
    if (!jwtUtil.validateToken(jwt)) {
      return Optional.empty();
    }

    try {
      // Step 2: If the token is cryptographically valid, extract the username.
      String username = jwtUtil.getUsernameFromToken(jwt);

      // Step 3: Load the user's details, potentially from cache
      UserDetails userDetails = loadUserDetails(username);

      // Step 4: Create a fully authenticated token for the SecurityContext.
      // This is the standard object Spring Security uses to represent the current user's session.
      Authentication authentication =
          new UsernamePasswordAuthenticationToken(
              userDetails, // The principal (the user object itself).
              null, // Credentials (like a password) are not needed post-authentication.
              userDetails.getAuthorities() // The user's roles and permissions.
              );
      log.debug("Successfully authenticated user '{}' from JWT.", username);
      return Optional.of(authentication);

    } catch (Exception e) {
      // This catch block handles errors from both getUsernameFromToken (e.g., parsing errors)
      // and loadUserByUsername (e.g., UserNotFoundException).
      log.error("Failed to create authentication object from JWT: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Load user details, first checking the cache if enabled.
   *
   * @param username The username to load details for
   * @return The UserDetails object
   */
  private UserDetails loadUserDetails(String username) {
    if (!cacheEnabled) {
      return userDetailsService.loadUserByUsername(username);
    }

    // Check cache first
    CachedUserDetails cachedDetails = userDetailsCache.get(username);
    if (cachedDetails != null && !cachedDetails.isExpired()) {
      log.debug("User details for '{}' found in cache", username);
      return cachedDetails.getUserDetails();
    }

    // Cache miss or expired, load from service
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

    // Cache the result
    userDetailsCache.put(username, new CachedUserDetails(userDetails, cacheExpirationMs));
    log.debug("User details for '{}' loaded from service and cached", username);

    return userDetails;
  }

  /** Clear expired entries from the cache. */
  private void cleanupCache() {
    try {
      int initialSize = userDetailsCache.size();

      userDetailsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

      int removed = initialSize - userDetailsCache.size();
      if (removed > 0) {
        log.info("Cleaned up {} expired user details from cache", removed);
      }
    } catch (Exception e) {
      log.error("Error during user details cache cleanup", e);
    }
  }

  /** Clear the entire cache. */
  public void clearCache() {
    userDetailsCache.clear();
    log.info("User details cache cleared");
  }

  /**
   * Remove a specific user from the cache.
   *
   * @param username The username to remove
   */
  public void removeFromCache(String username) {
    userDetailsCache.remove(username);
    log.debug("User '{}' removed from cache", username);
  }

  /** A container class for cached UserDetails with expiration. */
  private static class CachedUserDetails {
    private final UserDetails userDetails;
    private final long expirationTime;

    public CachedUserDetails(UserDetails userDetails, long cacheExpirationMs) {
      this.userDetails = userDetails;
      this.expirationTime = System.currentTimeMillis() + cacheExpirationMs;
    }

    public UserDetails getUserDetails() {
      return userDetails;
    }

    public boolean isExpired() {
      return System.currentTimeMillis() > expirationTime;
    }
  }
}
