package com.pawmento.global.exception;

public abstract class PermissionDeniedException extends RuntimeException {
  protected PermissionDeniedException(String message) {
    super(message);
  }

  protected PermissionDeniedException(String message, Throwable cause) {
    super(message, cause);
  }
}
