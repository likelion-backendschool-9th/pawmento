package com.pawmento.global.exception;

public abstract class ContentLimitExceededException extends RuleViolationException {
  protected ContentLimitExceededException(String message) {
    super(message);
  }

  protected ContentLimitExceededException(String message, Throwable cause) {
    super(message, cause);
  }
}
