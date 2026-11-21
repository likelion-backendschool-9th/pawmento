package com.pawmento.postcard.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPostcardRequestException extends RuleViolationException {
  public InvalidPostcardRequestException(String message) {
    super(message);
  }
}
