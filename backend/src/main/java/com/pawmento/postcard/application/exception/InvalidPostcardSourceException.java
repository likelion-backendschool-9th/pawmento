package com.pawmento.postcard.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPostcardSourceException extends RuleViolationException {
  public InvalidPostcardSourceException() {
    super("엽서로 만들 수 없는 원본이에요.");
  }
}
