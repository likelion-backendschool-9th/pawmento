package com.pawmento.media.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class MediaValidationFailedException extends RuleViolationException {
  private final String reason;

  public MediaValidationFailedException(String reason) {
    super("파일 검증에 실패했어요.");
    this.reason = reason;
  }

  public String reason() {
    return reason;
  }
}
