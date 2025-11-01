package com.poc.grpc.order.security;

import io.grpc.*;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * @class GrpcAuthInterceptor
 * @description A gRPC server interceptor that secures service endpoints by validating a JWT.
 * It extracts the token, uses JwtAuthenticator to verify it, and sets the
 * Spring Security context for the duration of the call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@GrpcGlobalServerInterceptor // Automatically applies this interceptor to all gRPC services.
public class GrpcAuthInterceptor implements ServerInterceptor {

    private final JwtAuthenticator jwtAuthenticator;

    // Defines the gRPC metadata key for the 'Authorization' header.
    public static final Metadata.Key<String> AUTHORIZATION_HEADER_KEY =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        try {
            final String authHeader = headers.get(AUTHORIZATION_HEADER_KEY);
            String jwt = null;

            // 1. Extract the JWT from the "Authorization: Bearer <token>" header.
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                jwt = authHeader.substring(BEARER_PREFIX.length()).trim();
            }

            // 2. If a token is present, attempt to authenticate.
            if (jwt != null) {
                // Delegate the complex authentication logic to the JwtAuthenticator.
                jwtAuthenticator.getAuthentication(jwt)
                        .ifPresent(SecurityContextHolder.getContext()::setAuthentication);
            } else {
                log.trace("No JWT found in Authorization header, proceeding as anonymous.");
            }

            // 3. CRITICAL: Forward the call, but wrap the listener to ensure the SecurityContext is
            // cleared *after* the call completes or is cancelled. This prevents context leakage
            // between different requests handled by the same thread.
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
            // 4. If any unexpected error occurs during auth, fail the call.
            log.warn("gRPC authentication failed with an unexpected error: {}", e.getMessage(), e);
            call.close(Status.UNAUTHENTICATED.withDescription(e.getMessage()).withCause(e), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}