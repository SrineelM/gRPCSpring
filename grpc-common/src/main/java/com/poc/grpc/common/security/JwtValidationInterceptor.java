package com.poc.grpc.common.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A gRPC client interceptor that performs a pre-emptive validation check on a JWT.
 *
 * <p>This interceptor inspects the headers of an outgoing gRPC call and, if an 'Authorization'
 * header is present, validates the JWT within it. This is an unusual pattern, as token validation
 * is typically a server-side responsibility. It assumes that another interceptor (like {@link
 * JwtAuthorizationInterceptor}) has already added the token to the headers.
 *
 * <p>If validation fails, the call is terminated immediately with an {@code UNAUTHENTICATED}
 * status, preventing the invalid request from being sent to the server.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtValidationInterceptor implements ClientInterceptor {

  // Utility for parsing and validating JWTs.
  private final JwtUtil jwtUtil;
  // Defines the metadata key for the 'Authorization' header.
  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  // The standard prefix for JWTs in the Authorization header.
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    log.debug(
        "Client Interceptor: Applying pre-emptive JWT validation for method: {}",
        method.getFullMethodName());

    // Use a ForwardingClientCall to inspect the headers before the call is sent.
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        try {
          // Retrieve the 'Authorization' header from the outgoing metadata.
          String authHeader = headers.get(AUTHORIZATION_HEADER);

          // Check if the header exists and is correctly formatted.
          if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            // Extract the raw token string.
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            // Perform the validation. This will throw an exception if the token is invalid
            // (e.g., bad signature, expired).
            jwtUtil.validateToken(token);
            log.debug("Pre-emptively validated JWT in outgoing gRPC call headers.");
          } else {
            // If no token is found, this interceptor does nothing and the call proceeds.
            log.warn(
                "No 'Authorization' header found for pre-emptive validation on call to {}",
                method.getFullMethodName());
          }
          // If validation is successful (or no token was found), proceed with the call.
          super.start(responseListener, headers);
        } catch (Exception e) {
          // If any validation exception occurs, terminate the call immediately.
          log.error("Client-side JWT validation failed, cancelling gRPC call.", e);
          // Close the call with an UNAUTHENTICATED status to provide clear feedback.
          responseListener.onClose(
              Status.UNAUTHENTICATED.withDescription("Invalid or missing JWT token").withCause(e),
              new Metadata());
        }
      }
    };
  }
}
