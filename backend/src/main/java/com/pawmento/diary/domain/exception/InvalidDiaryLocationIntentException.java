package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidDiaryLocationIntentException extends RuleViolationException {
  public InvalidDiaryLocationIntentException(String message) {
    super(message);
  }
}
