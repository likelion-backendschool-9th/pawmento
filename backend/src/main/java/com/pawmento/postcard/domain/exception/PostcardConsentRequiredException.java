package com.pawmento.postcard.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class PostcardConsentRequiredException extends RuleViolationException {
  public PostcardConsentRequiredException() {
    super("외부 AI 서비스로 보내는 데 동의해 주세요.");
  }
}
