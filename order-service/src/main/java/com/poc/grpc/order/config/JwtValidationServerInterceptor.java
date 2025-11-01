package com.poc.grpc.order.config;

import com.poc.grpc.common.security.JwtUtil;
import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@GrpcGlobalServerInterceptor
@RequiredArgsConstructor
public class JwtValidationServerInterceptor implements ServerInterceptor {

  private final JwtUtil jwtUtil;
  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    String methodName = call.getMethodDescriptor().getFullMethodName();
    String authHeader = headers.get(AUTHORIZATION_HEADER);

    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      String token = authHeader.substring(BEARER_PREFIX.length()).trim();
      if (!jwtUtil.validateToken(token)) {
        log.warn("Invalid JWT token in request to method: {}", methodName);
        call.close(
            Status.UNAUTHENTICATED.withDescription("Invalid authentication token"), new Metadata());
        return new ServerCall.Listener<>() {};
      }
      // Optionally: extract claims if needed
    } else {
      log.warn("No JWT token found in request to method: {}", methodName);
      call.close(
          Status.UNAUTHENTICATED.withDescription("Missing authentication token"), new Metadata());
      return new ServerCall.Listener<>() {};
    }

    return next.startCall(call, headers);
  }
}
