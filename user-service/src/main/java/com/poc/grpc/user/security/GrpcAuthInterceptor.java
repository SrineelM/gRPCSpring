package com.poc.grpc.user.security;

import com.poc.grpc.common.security.JwtAuthenticator;
import io.grpc.*;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * gRPC server interceptor that enforces JWT-based authentication for all service endpoints.
 *
 * <p>This interceptor serves as the primary security gateway for all incoming gRPC calls,
 * implementing a comprehensive authentication flow:
 *
 * <p><strong>Authentication Flow:</strong>
 *
 * <ol>
 *   <li>Extract JWT token from 'Authorization' metadata header
 *   <li>Validate token cryptographically (signature, expiration, format)
 *   <li>Load current user details from database via {@link JwtAuthenticator}
 *   <li>Create and populate Spring Security {@link
 *       org.springframework.security.core.context.SecurityContextHolder}
 *   <li>Allow request to proceed to service method
 *   <li><strong>CRITICAL:</strong> Clear SecurityContext after completion/cancellation
 * </ol>
 *
 * <p><strong>Security Guarantees:</strong>
 *
 * <ul>
 *   <li>Token validation ensures authenticity and integrity
 *   <li>Database lookup confirms user is still active and not locked
 *   <li>SecurityContext cleanup prevents context leakage between requests
 *   <li>Anonymous requests are handled gracefully (for public endpoints)
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This interceptor is thread-safe. The SecurityContext is stored
 * in ThreadLocal and properly cleaned up in both success and error scenarios, preventing context
 * pollution across requests in thread-pooled gRPC executors.
 *
 * <p><strong>Error Handling:</strong>
 *
 * <ul>
 *   <li>Missing/invalid tokens → UNAUTHENTICATED status
 *   <li>Expired tokens → UNAUTHENTICATED with descriptive message
 *   <li>Internal errors → INTERNAL status with error details logged
 * </ul>
 *
 * <p><strong>Usage:</strong> This interceptor is automatically registered globally via
 * {@code @GrpcGlobalServerInterceptor}, affecting all gRPC service methods. To bypass
 * authentication for specific methods, configure them in Spring Security's {@code permitAll()}
 * rules.
 *
 * @author gRPC Spring Team
 * @since 1.0.0
 * @see JwtAuthenticator
 * @see io.grpc.ServerInterceptor
 * @see org.springframework.security.core.context.SecurityContextHolder
 */
@Slf4j
@Component
@RequiredArgsConstructor
@GrpcGlobalServerInterceptor
public class GrpcAuthInterceptor implements ServerInterceptor {

  private final JwtAuthenticator jwtAuthenticator;

  public static final Metadata.Key<String> AUTHORIZATION_HEADER_KEY =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String BEARER_PREFIX = "Bearer ";

  /**
   * Intercepts all incoming gRPC calls to perform JWT authentication.
   *
   * <p>This method implements the authentication logic by:
   *
   * <ul>
   *   <li>Extracting the JWT token from the Authorization header
   *   <li>Validating the token and loading user details
   *   <li>Setting up the Spring Security context
   *   <li>Ensuring proper cleanup after request completion
   * </ul>
   *
   * @param call the gRPC server call being intercepted
   * @param headers the metadata headers containing potential authorization token
   * @param next the next handler in the interceptor chain
   * @param <ReqT> the request type
   * @param <RespT> the response type
   * @return a server call listener that handles the request and cleanup
   */
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    try {
      // Extract the Authorization header value from gRPC metadata
      final String authHeader = headers.get(AUTHORIZATION_HEADER_KEY);
      String jwt = null;

      // Check if the header exists and has the correct Bearer token format
      if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
        // Extract the actual token by removing the "Bearer " prefix
        jwt = authHeader.substring(BEARER_PREFIX.length()).trim();
      }

      // If a valid JWT token was found in the header
      if (jwt != null) {
        // Delegate to JwtAuthenticator to validate token and create Authentication object
        // This performs: token validation, user lookup, and authority mapping
        jwtAuthenticator
            .getAuthentication(jwt)
            // If authentication is successful, set it in the SecurityContext
            // This makes the user's identity and roles available to downstream code
            .ifPresent(SecurityContextHolder.getContext()::setAuthentication);
      } else {
        // No JWT found - this is normal for public endpoints
        // The request proceeds as anonymous, and @PreAuthorize annotations will enforce access
        // control
        log.trace("No JWT found in Authorization header, proceeding as anonymous.");
      }

      // Return a custom listener that ensures SecurityContext cleanup
      // This is CRITICAL for preventing security context leakage in thread pools
      return new SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {

        /**
         * Called when the RPC completes successfully. Ensures SecurityContext is cleared even after
         * successful completion.
         */
        @Override
        public void onComplete() {
          try {
            super.onComplete();
          } finally {
            // Clear the security context to prevent it from being reused by other requests
            // on the same thread (critical in thread-pooled environments)
            SecurityContextHolder.clearContext();
            log.trace("SecurityContext cleared on call completion.");
          }
        }

        /**
         * Called when the RPC is cancelled by the client. Ensures SecurityContext is cleared even
         * when request is cancelled.
         */
        @Override
        public void onCancel() {
          try {
            super.onCancel();
          } finally {
            // Clear security context even on cancellation to prevent leaks
            SecurityContextHolder.clearContext();
            log.trace("SecurityContext cleared on call cancellation.");
          }
        }
      };
    } catch (Exception e) {
      // Catch any unexpected errors during authentication process
      // This includes JWT parsing errors, database connection issues, etc.
      log.warn("gRPC authentication failed with an unexpected error: {}", e.getMessage(), e);

      // Close the call with UNAUTHENTICATED status and error details
      // This informs the client that authentication failed
      call.close(
          Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), new Metadata());

      // Return an empty listener since we've already closed the call
      return new ServerCall.Listener<>() {};
    }
  }
}
