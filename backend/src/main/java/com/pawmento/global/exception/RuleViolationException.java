package com.pawmento.global.exception;

public abstract class RuleViolationException extends RuntimeException {
  protected RuleViolationException(String message) {
    super(message);
  }

  protected RuleViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
