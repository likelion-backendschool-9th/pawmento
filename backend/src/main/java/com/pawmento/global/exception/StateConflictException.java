package com.pawmento.global.exception;

public abstract class StateConflictException extends RuntimeException {
  protected StateConflictException(String message) {
    super(message);
  }

  protected StateConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
