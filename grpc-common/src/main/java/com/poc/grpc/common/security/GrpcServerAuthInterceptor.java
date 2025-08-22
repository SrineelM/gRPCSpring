package com.poc.grpc.common.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * gRPC Server Authentication Interceptor.
 *
 * <p>This interceptor is a key component for integrating gRPC with Spring Security. It runs for
 * every incoming gRPC request, extracts the JWT from the 'Authorization' header, validates it, and
 * populates the {@link SecurityContextHolder} with an {@link Authentication} object.
 *
 * <p>By setting the security context, it enables the use of standard Spring Security annotations
 * like {@code @PreAuthorize} on gRPC service methods for fine-grained access control.
 *
 * <p>It also ensures that the security context is cleared after each call to prevent state leakage
 * between requests in the server's thread pool.
 *
 * <p>BEGINNER NOTE: Use this if you want to use Spring Security annotations (like @PreAuthorize) in
 * your gRPC services. This is more advanced than simple token validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServerAuthInterceptor implements ServerInterceptor {

  // Utility class for handling JWT parsing and validation.
  private final JwtUtil jwtUtil;
  // Defines the metadata key for the 'Authorization' header.
  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  // The standard prefix for JWTs in the Authorization header.
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    log.debug("Intercepting gRPC call to method: {}", methodName);

    try {
      // Attempt to extract the JWT from the 'Authorization' header.
      String authHeader = headers.get(AUTHORIZATION_HEADER);
      if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
        // Isolate the token from the "Bearer " prefix.
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        log.debug("Found JWT token in request headers for method: {}", methodName);

        // Validate the token's signature and claims.
        if (jwtUtil.validateToken(token)) {
          // If valid, create a Spring Security Authentication object from the token's claims.
          Authentication auth =
              jwtUtil
                  .getAuthentication(token)
                  .orElseThrow(() -> new SecurityException("Invalid authentication token claims"));
          // Set the Authentication object in the SecurityContextHolder. This makes the user's
          // identity and roles available for authorization checks (e.g., @PreAuthorize).
          SecurityContextHolder.getContext().setAuthentication(auth);
          log.debug(
              "Authentication context set for user '{}' in call to {}", auth.getName(), methodName);
        } else {
          // If the token is present but invalid (e.g., expired, bad signature).
          log.warn("Invalid JWT token in request to method: {}", methodName);
          // Reject the call immediately.
          throw new SecurityException("Invalid authentication token");
        }
      } else {
        // No 'Authorization' header was found. The request is treated as anonymous.
        log.debug(
            "No JWT token found in request to method: {}. Proceeding anonymously.", methodName);
        // Ensure the context is empty for this call. Downstream security rules will determine
        // if anonymous access is permitted for this specific method.
        SecurityContextHolder.clearContext();
      }

      // Proceed with the call, but wrap the listener to ensure cleanup happens afterwards.
      // This is the standard, robust way to manage request-scoped resources in gRPC interceptors.
      ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
      return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
        @Override
        public void onComplete() {
          try {
            super.onComplete();
          } finally {
            // Cleanup must happen regardless of whether the call completes successfully.
            cleanup();
          }
        }

        @Override
        public void onCancel() {
          try {
            super.onCancel();
          } finally {
            // Cleanup must also happen if the client cancels the call.
            cleanup();
          }
        }

        /**
         * Clears the SecurityContextHolder. This is a critical step to prevent the authentication
         * details of one request from leaking into another request that might be processed by the
         * same thread.
         */
        private void cleanup() {
          SecurityContextHolder.clearContext();
          log.debug("Security context cleared after gRPC call to {}", methodName);
        }
      };

    } catch (SecurityException e) {
      // Catches security-related exceptions thrown during token processing.
      log.warn("Security error in gRPC call to {}: {}", methodName, e.getMessage());
      // Close the call with UNAUTHENTICATED status, providing clear feedback to the client.
      call.close(
          Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), new Metadata());
      // Return a no-op listener as the call is terminated.
      return new ServerCall.Listener<>() {};
    } catch (Exception e) {
      // A general catch-all for any other unexpected errors during the interception logic.
      log.error(
          "Unexpected error in gRPC authentication for method {}: {}",
          methodName,
          e.getMessage(),
          e);
      // Close the call with INTERNAL status to indicate a server-side problem.
      call.close(
          Status.INTERNAL.withDescription("Internal security error").withCause(e), new Metadata());
      return new ServerCall.Listener<>() {};
    }
  }
}
