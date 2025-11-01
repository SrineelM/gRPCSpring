package com.poc.grpc.common.security.interceptors;

import com.poc.grpc.common.security.JwtAuthenticator;
import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

/**
 * Default implementation of JWT server interceptor with enhanced capabilities:
 *
 * <ul>
 *   <li>Detailed metrics tracking for authentication attempts
 *   <li>Rate limiting for repeated failures
 *   <li>Performance monitoring
 * </ul>
 */
@Slf4j
public class DefaultJwtServerInterceptor extends AbstractJwtServerInterceptor {

  // Metrics tracking
  private final LongAdder totalRequests = new LongAdder();
  private final LongAdder authenticatedRequests = new LongAdder();
  private final LongAdder failedRequests = new LongAdder();
  private final LongAdder expiredTokens = new LongAdder();
  private final LongAdder invalidTokens = new LongAdder();
  private final LongAdder missingTokens = new LongAdder();

  // Performance tracking
  private final Map<String, LongAdder> methodCallCounts = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> methodTotalTime = new ConcurrentHashMap<>();

  // Failed authentication tracking for rate limiting
  private final Map<String, FailedAuthEntry> failedAuthAttempts = new ConcurrentHashMap<>();

  // Scheduler for metrics logging
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  // Maximum failed attempts before temporary blocking
  private static final int MAX_FAILED_ATTEMPTS = 5;
  // Block duration in milliseconds (5 minutes)
  private static final long BLOCK_DURATION_MS = 300000;

  private final MeterRegistry meterRegistry;

  /**
   * Creates a new DefaultJwtServerInterceptor.
   *
   * @param jwtUtil JWT utility for token operations
   * @param securityProperties Security configuration properties
   * @param jwtAuthenticator JWT authenticator for token authentication
   * @param meterRegistry Meter registry for metrics tracking
   */
  public DefaultJwtServerInterceptor(
      JwtUtil jwtUtil,
      SecurityConfigurationProperties securityProperties,
      JwtAuthenticator jwtAuthenticator,
      MeterRegistry meterRegistry) {
    super(jwtUtil, securityProperties, jwtAuthenticator);
    this.meterRegistry = meterRegistry;

    // Schedule periodic metrics logging
    scheduler.scheduleAtFixedRate(
        this::logMetrics,
        60, // Initial delay: 60 seconds
        60, // Period: 60 seconds
        TimeUnit.SECONDS);

    // Add shutdown hook
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    scheduler.shutdownNow();
                    log.info("Metrics scheduler shut down");
                  } catch (Exception e) {
                    log.error("Error shutting down metrics scheduler", e);
                  }
                }));
  }

  @Override
  protected Optional<Authentication> customAuthenticate(String jwt) {
    totalRequests.increment();
    meterRegistry.counter("auth.attempts").increment();
    try {
      // Check if the client IP is temporarily blocked due to too many failures
      String clientIp = getCurrentClientIp();
      if (isClientBlocked(clientIp)) {
        failedRequests.increment();
        meterRegistry.counter("auth.failures", "reason", "blocked").increment();
        log.warn(
            "Request from client IP {} blocked due to too many authentication failures", clientIp);
        return Optional.empty(); // This will cause authentication to fail
      }

      // Let the parent class handle the actual authentication
      Optional<Authentication> result = super.customAuthenticate(jwt);

      if (result.isPresent()) {
        authenticatedRequests.increment();
        meterRegistry.counter("auth.success").increment();

        // Reset failed attempts counter on successful authentication
        if (clientIp != null) {
          failedAuthAttempts.remove(clientIp);
        }
      } else {
        failedRequests.increment();
        meterRegistry.counter("auth.failures", "reason", "invalid").increment();

        // Since authentication failed, track the failure type if possible
        if (jwt == null || jwt.isEmpty()) {
          missingTokens.increment();
          meterRegistry.counter("auth.failures", "reason", "missing_token").increment();
        } else if (!jwtUtil.validateToken(jwt)) {
          // Check if token is expired or otherwise invalid
          try {
            if (jwtUtil.isTokenExpired(jwt)) {
              expiredTokens.increment();
              meterRegistry.counter("auth.failures", "reason", "expired_token").increment();
            } else {
              invalidTokens.increment();
              meterRegistry.counter("auth.failures", "reason", "invalid_token").increment();
            }
          } catch (Exception e) {
            // If we can't parse the token at all, it's invalid
            invalidTokens.increment();
            meterRegistry.counter("auth.failures", "reason", "parse_error").increment();
          }
        }

        // Track failed attempts for rate limiting
        if (clientIp != null) {
          recordFailedAttempt(clientIp);
        }
      }

      return result;
    } catch (Exception e) {
      failedRequests.increment();
      meterRegistry.counter("auth.failures", "reason", "exception").increment();
      log.error("Error during authentication", e);
      return Optional.empty();
    }
  }

  @Override
  protected SecurityLevel getSecurityLevel() {
    return super.getSecurityLevel();
  }

  /**
   * Records the start time of a method call for performance tracking.
   *
   * @param methodName The method name
   * @return The start time in nanoseconds
   */
  public long recordMethodStart(String methodName) {
    methodCallCounts.computeIfAbsent(methodName, k -> new LongAdder()).increment();
    return System.nanoTime();
  }

  /**
   * Records the end time of a method call for performance tracking.
   *
   * @param methodName The method name
   * @param startTimeNanos The start time in nanoseconds
   */
  public void recordMethodEnd(String methodName, long startTimeNanos) {
    long durationNanos = System.nanoTime() - startTimeNanos;
    methodTotalTime.computeIfAbsent(methodName, k -> new AtomicLong()).addAndGet(durationNanos);
  }

  /** Logs current metrics. */
  private void logMetrics() {
    try {
      log.info(
          "JWT Authentication Metrics - Total: {}, Authenticated: {}, Failed: {}, "
              + "Expired Tokens: {}, Invalid Tokens: {}, Missing Tokens: {}",
          totalRequests.sum(),
          authenticatedRequests.sum(),
          failedRequests.sum(),
          expiredTokens.sum(),
          invalidTokens.sum(),
          missingTokens.sum());

      // Log method-specific metrics
      if (!methodCallCounts.isEmpty()) {
        StringBuilder sb = new StringBuilder("Method Performance Metrics:\n");
        methodCallCounts.forEach(
            (method, count) -> {
              long totalTimeNanos = methodTotalTime.getOrDefault(method, new AtomicLong()).get();
              double avgTimeMs = count.sum() > 0 ? (totalTimeNanos / 1_000_000.0) / count.sum() : 0;
              sb.append(
                  String.format(
                      "  %s - Count: %d, Avg Time: %.2fms\n", method, count.sum(), avgTimeMs));
            });
        log.info(sb.toString());
      }

      // Log rate limiting metrics
      int blockedClients =
          (int) failedAuthAttempts.values().stream().filter(FailedAuthEntry::isBlocked).count();

      if (blockedClients > 0) {
        log.info("Rate limiting - Blocked clients: {}", blockedClients);
      }
    } catch (Exception e) {
      log.error("Error logging metrics", e);
    }
  }

  /**
   * Gets the current client IP address.
   *
   * @return The client IP address, or null if not available
   */
  private String getCurrentClientIp() {
    // In a real implementation, this would extract the client IP from the gRPC context
    // For simplicity, we'll just return a placeholder
    return "127.0.0.1";
  }

  /**
   * Records a failed authentication attempt.
   *
   * @param clientIp The client IP address
   */
  private void recordFailedAttempt(String clientIp) {
    FailedAuthEntry entry =
        failedAuthAttempts.computeIfAbsent(clientIp, k -> new FailedAuthEntry());
    entry.incrementAttempts();

    if (entry.getAttempts() >= MAX_FAILED_ATTEMPTS) {
      entry.block();
      log.warn(
          "Client IP {} blocked for {} minutes due to too many authentication failures",
          clientIp,
          BLOCK_DURATION_MS / 60000);
    }
  }

  /**
   * Checks if a client IP is blocked due to too many failed authentication attempts.
   *
   * @param clientIp The client IP address
   * @return true if the client is blocked, false otherwise
   */
  private boolean isClientBlocked(String clientIp) {
    FailedAuthEntry entry = failedAuthAttempts.get(clientIp);
    if (entry != null) {
      return entry.isBlocked();
    }
    return false;
  }

  /** Class representing a failed authentication entry. */
  private static class FailedAuthEntry {
    private final AtomicLong attempts = new AtomicLong(0);
    private volatile long blockUntil = 0;

    /**
     * Increments the number of failed attempts.
     *
     * @return The new number of attempts
     */
    long incrementAttempts() {
      return attempts.incrementAndGet();
    }

    /**
     * Gets the number of failed attempts.
     *
     * @return The number of attempts
     */
    long getAttempts() {
      return attempts.get();
    }

    /** Blocks the client for the configured duration. */
    void block() {
      blockUntil = System.currentTimeMillis() + BLOCK_DURATION_MS;
    }

    /**
     * Checks if the client is currently blocked.
     *
     * @return true if the client is blocked, false otherwise
     */
    boolean isBlocked() {
      return System.currentTimeMillis() < blockUntil;
    }
  }
}
