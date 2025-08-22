package com.poc.grpc.common.security.util;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class GrpcErrorHandler {
  public static StatusRuntimeException handleAuthError(String message, Throwable ex) {
    return Status.UNAUTHENTICATED.withDescription(message).withCause(ex).asRuntimeException();
  }

  public static StatusRuntimeException handleValidationError(String message, Throwable ex) {
    return Status.INVALID_ARGUMENT.withDescription(message).withCause(ex).asRuntimeException();
  }
}
