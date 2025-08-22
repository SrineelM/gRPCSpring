package com.poc.grpc.common.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A gRPC client interceptor that automatically adds a JWT to outgoing requests.
 *
 * <p>This interceptor is crucial for propagating a user's identity between microservices. It works
 * by:
 *
 * <ol>
 *   <li>Retrieving the current user's {@link Authentication} object from the {@link
 *       SecurityContextHolder}.
 *   <li>Generating a new JWT based on that authentication object.
 *   <li>Injecting the JWT into the 'Authorization' header of the outgoing gRPC call.
 * </ol>
 *
 * <p>If no authenticated user is found in the security context, the call proceeds without an
 * Authorization header, and the downstream service will treat it as an anonymous request.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthorizationInterceptor implements ClientInterceptor {

  // Utility for generating JWTs from an Authentication object.
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
        "Client Interceptor: Preparing to add authorization to gRPC call for method: {}",
        method.getFullMethodName());

    // Use a ForwardingClientCall to intercept the start of the call and modify its headers.
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        next.newCall(method, callOptions)) {
      @Override
      public void start(Listener<RespT> responseListener, Metadata headers) {
        try {
          // Retrieve the current user's authentication details from the thread-local
          // SecurityContext.
          Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
          // Check if there is an authenticated user in the current context.
          if (authentication != null && authentication.isAuthenticated()) {
            // If authenticated, generate a new JWT to propagate the user's identity.
            String token =
                jwtUtil.generateToken(authentication); // This can throw JwtGenerationException
            // Add the JWT to the 'Authorization' header of the outgoing gRPC call.
            headers.put(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
            log.debug(
                "Added JWT token to gRPC call headers for user: {}", authentication.getName());
          } else {
            // If no authentication is found, proceed without an Authorization header.
            // The downstream service will treat this as an anonymous request.
            log.debug(
                "No authentication context found for gRPC call to {}", method.getFullMethodName());
          }
          // Proceed with starting the call, now with the potentially modified headers.
          super.start(responseListener, headers);
        } catch (Exception e) {
          // If any error occurs during token generation, fail the call immediately.
          log.error("Failed to generate and add JWT to gRPC call: {}", e.getMessage(), e);
          // Close the call with an UNAUTHENTICATED status to signal a security failure to the
          // caller.
          responseListener.onClose(
              Status.UNAUTHENTICATED
                  .withDescription("Failed to add authentication token")
                  .withCause(e),
              new Metadata());
        }
      }
    };
  }
}
