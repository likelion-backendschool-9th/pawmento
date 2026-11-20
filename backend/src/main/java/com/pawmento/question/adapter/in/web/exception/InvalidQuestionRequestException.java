package com.pawmento.question.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidQuestionRequestException extends RuleViolationException {
  public InvalidQuestionRequestException() {
    super("petId·조회 조건·답변 본문·버전을 확인해 주세요.");
  }
}
