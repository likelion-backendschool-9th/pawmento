package com.pawmento.global.exception;

public abstract class ResourceNotFoundException extends RuntimeException {
  protected ResourceNotFoundException(String message) {
    super(message);
  }

  protected ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
