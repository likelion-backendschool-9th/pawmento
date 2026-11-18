package com.pawmento.global.exception;

public abstract class UnsupportedFormatException extends RuleViolationException {
  protected UnsupportedFormatException(String message) {
    super(message);
  }

  protected UnsupportedFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
