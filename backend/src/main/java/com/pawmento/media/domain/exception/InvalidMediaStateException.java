package com.pawmento.media.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidMediaStateException extends RuleViolationException {
  public InvalidMediaStateException() {
    super("저장된 미디어 상태가 올바르지 않아요.");
  }
}
