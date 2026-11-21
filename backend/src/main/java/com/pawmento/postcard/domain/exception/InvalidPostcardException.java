package com.pawmento.postcard.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPostcardException extends RuleViolationException {
  public InvalidPostcardException(String message) {
    super(message);
  }
}
