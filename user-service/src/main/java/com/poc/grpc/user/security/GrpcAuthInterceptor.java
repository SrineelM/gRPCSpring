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
 * A gRPC server interceptor that secures service endpoints by validating a JWT. This class acts as
 * the primary security gate for all incoming gRPC calls.
 *
 * <p>Its responsibilities are: 1. Intercept every gRPC call server-wide, thanks to the
 * {@code @GrpcGlobalServerInterceptor} annotation. 2. Extract the JWT from the 'Authorization'
 * metadata header. 3. Delegate the token validation and user details loading to the {@link
 * JwtAuthenticator}. 4. Populate the {@link SecurityContextHolder} with a valid {@code
 * Authentication} object. 5. CRUCIALLY, ensure the {@code SecurityContextHolder} is cleared after
 * the call completes or is cancelled, preventing security context leakage between requests on the
 * same thread.
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

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    try {
      final String authHeader = headers.get(AUTHORIZATION_HEADER_KEY);
      String jwt = null;
      if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
        jwt = authHeader.substring(BEARER_PREFIX.length()).trim();
      }
      if (jwt != null) {
        jwtAuthenticator
            .getAuthentication(jwt)
            .ifPresent(SecurityContextHolder.getContext()::setAuthentication);
      } else {
        log.trace("No JWT found in Authorization header, proceeding as anonymous.");
      }
      return new SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
        @Override
        public void onComplete() {
          try {
            super.onComplete();
          } finally {
            SecurityContextHolder.clearContext();
            log.trace("SecurityContext cleared on call completion.");
          }
        }

        @Override
        public void onCancel() {
          try {
            super.onCancel();
          } finally {
            SecurityContextHolder.clearContext();
            log.trace("SecurityContext cleared on call cancellation.");
          }
        }
      };
    } catch (Exception e) {
      log.warn("gRPC authentication failed with an unexpected error: {}", e.getMessage(), e);
      call.close(
          Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), new Metadata());
      return new ServerCall.Listener<>() {};
    }
  }
}
