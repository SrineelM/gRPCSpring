package com.poc.grpc.common.security.interceptors;

import com.poc.grpc.common.security.JwtAuthenticator;
import com.poc.grpc.common.security.JwtUtil;
import com.poc.grpc.common.security.core.SecurityConfigurationProperties;
import com.poc.grpc.common.security.enums.SecurityLevel;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for all gRPC server interceptors that handle JWT authentication. This class
 * implements the Template Method pattern to provide consistent JWT handling while allowing
 * subclasses to customize specific behaviors.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractJwtServerInterceptor implements ServerInterceptor {

  protected final JwtUtil jwtUtil;
  protected final SecurityConfigurationProperties securityProperties;
  protected final JwtAuthenticator jwtAuthenticator;

  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public final <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    log.debug("Intercepting gRPC call to method: {}", methodName);

    // Check if method is excluded from authentication
    if (isMethodExcluded(methodName)) {
      log.debug("Method {} is excluded from authentication", methodName);
      return next.startCall(call, headers);
    }

    // Extract JWT token
    String jwt = extractJwtFromHeaders(headers);

    // Handle authentication based on security level
    SecurityLevel securityLevel = getSecurityLevel();

    try {
      switch (securityLevel) {
        case NONE:
          return handleNoSecurity(call, headers, next);

        case BASIC_VALIDATION:
          return handleBasicValidation(call, headers, next, jwt, methodName);

        case FULL_SPRING_SECURITY:
          return handleFullSpringSecurity(call, headers, next, jwt, methodName);

        default:
          log.warn("Unknown security level: {}, defaulting to basic validation", securityLevel);
          return handleBasicValidation(call, headers, next, jwt, methodName);
      }
    } catch (Exception e) {
      log.error("Unexpected error during authentication for method: {}", methodName, e);
      return handleAuthenticationFailure(call, "Internal authentication error");
    }
  }

  /** Extract JWT token from gRPC metadata headers */
  private String extractJwtFromHeaders(Metadata headers) {
    String authHeader = headers.get(AUTHORIZATION_HEADER);
    if (authHeader != null
        && StringUtils.hasText(authHeader)
        && authHeader.startsWith(BEARER_PREFIX)) {
      return authHeader.substring(BEARER_PREFIX.length()).trim();
    }
    return null;
  }

  /** Handle no security - allow all requests */
  private <ReqT, RespT> ServerCall.Listener<ReqT> handleNoSecurity(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    return next.startCall(call, headers);
  }

  /** Handle basic JWT validation without Spring Security integration */
  private <ReqT, RespT> ServerCall.Listener<ReqT> handleBasicValidation(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next,
      String jwt,
      String methodName) {

    if (!StringUtils.hasText(jwt)) {
      log.warn("No JWT token found for method: {}", methodName);
      return handleAuthenticationFailure(call, "Missing authentication token");
    }

    if (!jwtUtil.validateToken(jwt)) {
      log.warn("Invalid JWT token for method: {}", methodName);
      return handleAuthenticationFailure(call, "Invalid authentication token");
    }

    log.debug("JWT token validated successfully for method: {}", methodName);
    return next.startCall(call, headers);
  }

  /** Handle full Spring Security integration with SecurityContextHolder */
  private <ReqT, RespT> ServerCall.Listener<ReqT> handleFullSpringSecurity(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next,
      String jwt,
      String methodName) {

    if (!StringUtils.hasText(jwt)) {
      log.warn("No JWT token found for method: {}", methodName);
      return handleAuthenticationFailure(call, "Missing authentication token");
    }

    Optional<Authentication> authentication = jwtAuthenticator.getAuthentication(jwt);
    if (authentication.isEmpty()) {
      log.warn("JWT authentication failed for method: {}", methodName);
      return handleAuthenticationFailure(call, "Authentication failed");
    }

    // Set authentication in security context
    SecurityContextHolder.getContext().setAuthentication(authentication.get());
    log.debug(
        "Security context set for user: {} on method: {}",
        authentication.get().getName(),
        methodName);

    return new SecurityContextClearingListener<>(next.startCall(call, headers));
  }

  /** Handle authentication failure by closing the call with UNAUTHENTICATED status */
  private <ReqT, RespT> ServerCall.Listener<ReqT> handleAuthenticationFailure(
      ServerCall<ReqT, RespT> call, String description) {
    call.close(Status.UNAUTHENTICATED.withDescription(description), new Metadata());
    return new ServerCall.Listener<ReqT>() {};
  }

  /** Check if the method is excluded from authentication */
  private boolean isMethodExcluded(String methodName) {
    return Arrays.asList(securityProperties.getGrpc().getExcludedMethods()).contains(methodName);
  }

  /**
   * Get the security level for this interceptor. Subclasses can override this to provide custom
   * security levels.
   */
  protected SecurityLevel getSecurityLevel() {
    return securityProperties.getGrpc().getServerSecurityLevel();
  }

  /**
   * Template method for custom authentication logic. Subclasses can override this to add
   * service-specific authentication.
   */
  protected Optional<Authentication> customAuthenticate(String jwt) {
    return Optional.empty();
  }

  /** ServerCall.Listener that ensures SecurityContextHolder is cleared after the call */
  private static class SecurityContextClearingListener<ReqT>
      extends SimpleForwardingServerCallListener<ReqT> {

    protected SecurityContextClearingListener(ServerCall.Listener<ReqT> delegate) {
      super(delegate);
    }

    @Override
    public void onComplete() {
      try {
        super.onComplete();
      } finally {
        SecurityContextHolder.clearContext();
        log.trace("Security context cleared after successful call completion");
      }
    }

    @Override
    public void onCancel() {
      try {
        super.onCancel();
      } finally {
        SecurityContextHolder.clearContext();
        log.trace("Security context cleared after call cancellation");
      }
    }
  }
}
