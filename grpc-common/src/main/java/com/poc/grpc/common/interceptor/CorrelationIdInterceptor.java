package com.poc.grpc.common.interceptor;

import io.grpc.*;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * gRPC server interceptor that adds correlation ID tracking for distributed tracing.
 *
 * <p>This interceptor:
 *
 * <ul>
 *   <li>Extracts or generates a unique correlation ID for each request
 *   <li>Propagates the correlation ID through gRPC metadata
 *   <li>Adds the correlation ID to MDC for structured logging
 *   <li>Ensures correlation ID is cleaned up after request completion
 * </ul>
 *
 * <p>The correlation ID can be used to trace requests across multiple microservices, making it
 * easier to debug issues in distributed systems.
 *
 * @author gRPC Spring Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CorrelationIdInterceptor implements ServerInterceptor {

  /** Metadata key for correlation ID in gRPC headers. */
  public static final Metadata.Key<String> CORRELATION_ID_KEY =
      Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

  /** Context key for storing correlation ID in gRPC context. */
  public static final Context.Key<String> CORRELATION_ID_CONTEXT_KEY = Context.key("correlationId");

  /** MDC key for correlation ID in logging context. */
  public static final String MDC_CORRELATION_ID_KEY = "correlationId";

  /**
   * Intercepts gRPC calls to add correlation ID tracking.
   *
   * @param call the server call
   * @param headers the metadata headers
   * @param next the next handler in the chain
   * @return server call listener with correlation ID handling
   */
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

    // Extract correlation ID from headers or generate a new one
    String correlationId = extractOrGenerateCorrelationId(headers);

    // Add correlation ID to MDC for logging
    MDC.put(MDC_CORRELATION_ID_KEY, correlationId);

    // Log the incoming request with correlation ID
    String methodName = call.getMethodDescriptor().getFullMethodName();
    log.debug("Processing gRPC call: {} with correlationId: {}", methodName, correlationId);

    // Add correlation ID to response headers for client tracking
    Metadata responseHeaders = new Metadata();
    responseHeaders.put(CORRELATION_ID_KEY, correlationId);
    call.sendHeaders(responseHeaders);

    // Create context with correlation ID
    Context context = Context.current().withValue(CORRELATION_ID_CONTEXT_KEY, correlationId);

    // Return a listener that cleans up MDC after request completion
    return new SimpleForwardingServerCallListener<ReqT>(
        Contexts.interceptCall(context, call, headers, next)) {

      @Override
      public void onComplete() {
        try {
          super.onComplete();
          log.trace("gRPC call completed: {}", methodName);
        } finally {
          // Clean up MDC to prevent memory leaks and context pollution
          MDC.remove(MDC_CORRELATION_ID_KEY);
        }
      }

      @Override
      public void onCancel() {
        try {
          super.onCancel();
          log.debug("gRPC call cancelled: {}", methodName);
        } finally {
          // Clean up MDC even on cancellation
          MDC.remove(MDC_CORRELATION_ID_KEY);
        }
      }

      @Override
      public void onHalfClose() {
        try {
          super.onHalfClose();
        } catch (Exception e) {
          log.error("Error during onHalfClose for call: {}", methodName, e);
          throw e;
        }
      }
    };
  }

  /**
   * Extracts correlation ID from request headers or generates a new UUID if not present.
   *
   * @param headers the gRPC metadata headers
   * @return the correlation ID
   */
  private String extractOrGenerateCorrelationId(Metadata headers) {
    String correlationId = headers.get(CORRELATION_ID_KEY);

    if (correlationId == null || correlationId.trim().isEmpty()) {
      // Generate a new correlation ID if not provided
      correlationId = UUID.randomUUID().toString();
      log.trace("Generated new correlation ID: {}", correlationId);
    } else {
      log.trace("Using existing correlation ID from request: {}", correlationId);
    }

    return correlationId;
  }

  /**
   * Gets the current correlation ID from the gRPC context.
   *
   * <p>This method can be called from service implementations to access the correlation ID.
   *
   * @return the current correlation ID, or null if not set
   */
  public static String getCurrentCorrelationId() {
    return CORRELATION_ID_CONTEXT_KEY.get();
  }
}
