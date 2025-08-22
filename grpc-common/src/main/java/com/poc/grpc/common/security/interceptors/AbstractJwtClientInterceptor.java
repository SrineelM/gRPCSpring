package com.poc.grpc.common.security.interceptors;

import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import io.grpc.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for all gRPC client interceptors that handle JWT tokens. Provides consistent
 * token handling patterns for outgoing requests.
 *
 * <p>This implementation includes:
 *
 * <ul>
 *   <li>MDC logging context for correlation tracking
 *   <li>Detailed performance metrics logging
 *   <li>Comprehensive error logging
 *   <li>Support for token propagation and client-side validation
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractJwtClientInterceptor implements ClientInterceptor {

  protected final JwtUtil jwtUtil;
  protected final SecurityConfigurationProperties securityProperties;

  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> REQUEST_ID_HEADER =
      Metadata.Key.of("X-Request-ID", Metadata.ASCII_STRING_MARSHALLER);
  private static final Metadata.Key<String> CORRELATION_ID_HEADER =
      Metadata.Key.of("X-Correlation-ID", Metadata.ASCII_STRING_MARSHALLER);
  private static final String BEARER_PREFIX = "Bearer ";

  // MDC constants
  private static final String MDC_METHOD_NAME = "grpc.method";
  private static final String MDC_REQUEST_ID = "requestId";
  private static final String MDC_CORRELATION_ID = "correlationId";
  private static final String MDC_USER_ID = "userId";
  private static final String MDC_SECURITY_LEVEL = "securityLevel";

  @Override
  public final <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

    final String methodName = method.getFullMethodName();
    final String requestId = UUID.randomUUID().toString();
    final String correlationId =
        MDC.get(MDC_CORRELATION_ID) != null ? MDC.get(MDC_CORRELATION_ID) : requestId;

    // Set up MDC context for this call
    MDC.put(MDC_METHOD_NAME, methodName);
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CORRELATION_ID, correlationId);

    try {
      log.debug("Intercepting outgoing gRPC call to method: {}", methodName);

      SecurityLevel securityLevel = getSecurityLevel();
      MDC.put(MDC_SECURITY_LEVEL, securityLevel.name());

      // Extract user information for logging if available
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication != null && authentication.isAuthenticated()) {
        MDC.put(MDC_USER_ID, authentication.getName());
        log.debug(
            "Authenticated user: {} calling method: {}", authentication.getName(), methodName);
      }

      switch (securityLevel) {
        case TOKEN_PROPAGATION:
          log.debug("Using TOKEN_PROPAGATION security level for method: {}", methodName);
          return handleTokenPropagation(
              method, callOptions, next, methodName, requestId, correlationId);

        case CLIENT_VALIDATION:
          log.debug("Using CLIENT_VALIDATION security level for method: {}", methodName);
          return handleClientValidation(
              method, callOptions, next, methodName, requestId, correlationId);

        case NONE:
          log.debug("No security applied for method: {}", methodName);
          return wrapCallForMetrics(
              next.newCall(method, callOptions), methodName, requestId, correlationId);

        default:
          log.warn("Unknown client security level: {}, no token handling applied", securityLevel);
          return wrapCallForMetrics(
              next.newCall(method, callOptions), methodName, requestId, correlationId);
      }
    } finally {
      // Clear MDC context for this thread to prevent leaks
      MDC.remove(MDC_METHOD_NAME);
      MDC.remove(MDC_REQUEST_ID);
      MDC.remove(MDC_CORRELATION_ID);
      MDC.remove(MDC_USER_ID);
      MDC.remove(MDC_SECURITY_LEVEL);
    }
  }

  /** Handle token propagation - add JWT to outgoing requests */
  private <ReqT, RespT> ClientCall<ReqT, RespT> handleTokenPropagation(
      MethodDescriptor<ReqT, RespT> method,
      CallOptions callOptions,
      Channel next,
      String methodName,
      String requestId,
      String correlationId) {

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {

      private long startTimeNanos;

      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        startTimeNanos = System.nanoTime();

        try {
          // Add tracking headers
          headers.put(REQUEST_ID_HEADER, requestId);
          headers.put(CORRELATION_ID_HEADER, correlationId);

          // Get and add JWT token
          String jwt = getOrGenerateToken();
          if (StringUtils.hasText(jwt)) {
            headers.put(AUTHORIZATION_HEADER, BEARER_PREFIX + jwt);
            log.debug("JWT token added to outgoing call to method: {}", methodName);
          } else {
            log.debug("No JWT token available for outgoing call to method: {}", methodName);
          }

          // Wrap the response listener to track performance metrics
          Listener<RespT> wrappedListener =
              wrapResponseListener(responseListener, startTimeNanos, methodName);

          super.start(wrappedListener, headers);
        } catch (Exception e) {
          log.error("Error preparing outgoing call to method: {}", methodName, e);
          responseListener.onClose(
              Status.INTERNAL.withDescription("Error preparing call: " + e.getMessage()),
              new Metadata());
        }
      }
    };
  }

  /** Handle client-side validation - validate token before sending */
  private <ReqT, RespT> ClientCall<ReqT, RespT> handleClientValidation(
      MethodDescriptor<ReqT, RespT> method,
      CallOptions callOptions,
      Channel next,
      String methodName,
      String requestId,
      String correlationId) {

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {

      private long startTimeNanos;

      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        startTimeNanos = System.nanoTime();

        try {
          // Add tracking headers
          headers.put(REQUEST_ID_HEADER, requestId);
          headers.put(CORRELATION_ID_HEADER, correlationId);

          // Get and validate JWT token
          String jwt = getOrGenerateToken();

          if (StringUtils.hasText(jwt)) {
            try {
              if (jwtUtil.validateToken(jwt)) {
                headers.put(AUTHORIZATION_HEADER, BEARER_PREFIX + jwt);
                log.debug("Valid JWT token added to outgoing call to method: {}", methodName);
              } else {
                log.warn("Invalid JWT token, terminating call to method: {}", methodName);
                responseListener.onClose(
                    Status.UNAUTHENTICATED.withDescription("Invalid authentication token"),
                    new Metadata());
                return;
              }
            } catch (Exception e) {
              log.error("Error validating JWT token for method: {}", methodName, e);
              responseListener.onClose(
                  Status.INTERNAL.withDescription("Error validating token: " + e.getMessage()),
                  new Metadata());
              return;
            }
          } else {
            log.warn("No JWT token available for validated call to method: {}", methodName);
            responseListener.onClose(
                Status.UNAUTHENTICATED.withDescription("Missing authentication token"),
                new Metadata());
            return;
          }

          // Wrap the response listener to track performance metrics
          Listener<RespT> wrappedListener =
              wrapResponseListener(responseListener, startTimeNanos, methodName);

          super.start(wrappedListener, headers);
        } catch (Exception e) {
          log.error("Error preparing outgoing call to method: {}", methodName, e);
          responseListener.onClose(
              Status.INTERNAL.withDescription("Error preparing call: " + e.getMessage()),
              new Metadata());
        }
      }
    };
  }

  /** Wrap a call to add metrics and logging */
  private <ReqT, RespT> ClientCall<ReqT, RespT> wrapCallForMetrics(
      ClientCall<ReqT, RespT> call, String methodName, String requestId, String correlationId) {

    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(call) {
      private long startTimeNanos;

      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        startTimeNanos = System.nanoTime();

        // Add tracking headers
        headers.put(REQUEST_ID_HEADER, requestId);
        headers.put(CORRELATION_ID_HEADER, correlationId);

        // Wrap the response listener to track performance metrics
        Listener<RespT> wrappedListener =
            wrapResponseListener(responseListener, startTimeNanos, methodName);

        super.start(wrappedListener, headers);
      }
    };
  }

  /** Wrap a response listener to add metrics and logging */
  private <RespT> ClientCall.Listener<RespT> wrapResponseListener(
      ClientCall.Listener<RespT> listener, long startTimeNanos, String methodName) {

    return new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(listener) {
      @Override
      public void onClose(Status status, Metadata trailers) {
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);

        if (status.isOk()) {
          log.debug("Call to {} completed successfully in {}ms", methodName, duration);
        } else {
          log.warn(
              "Call to {} failed with status {} in {}ms: {}",
              methodName,
              status.getCode(),
              duration,
              status.getDescription());
        }

        super.onClose(status, trailers);
      }
    };
  }

  /** Get or generate JWT token for the current security context */
  private String getOrGenerateToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.isAuthenticated()) {
      try {
        // First try custom token generation
        String customToken = customGenerateToken(authentication);
        if (StringUtils.hasText(customToken)) {
          log.debug("Using custom generated token");
          return customToken;
        }

        // Fall back to standard token generation
        log.debug("Generating standard JWT token for authentication: {}", authentication.getName());
        return jwtUtil.generateToken(authentication);
      } catch (Exception e) {
        log.error("Failed to generate JWT token", e);
        return null;
      }
    }

    log.debug("No authentication found in security context");
    return null;
  }

  /**
   * Get the security level for this client interceptor. Subclasses can override this to provide
   * custom security levels.
   */
  protected SecurityLevel getSecurityLevel() {
    return securityProperties.getGrpc().getClientSecurityLevel();
  }

  /**
   * Template method for custom token generation logic. Subclasses can override this to add
   * service-specific token handling.
   *
   * @param authentication The current authentication
   * @return A custom JWT token, or null to use the default token generation
   */
  protected String customGenerateToken(Authentication authentication) {
    return null;
  }
}
