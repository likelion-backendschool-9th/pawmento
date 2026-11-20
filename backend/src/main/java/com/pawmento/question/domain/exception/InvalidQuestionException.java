package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidQuestionException extends RuleViolationException {
  public InvalidQuestionException() {
    super("질문의 모드·본문·시각·버전을 확인해 주세요.");
  }
}
