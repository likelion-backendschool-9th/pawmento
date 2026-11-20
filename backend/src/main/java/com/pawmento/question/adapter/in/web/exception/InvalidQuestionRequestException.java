package com.pawmento.question.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidQuestionRequestException extends RuleViolationException {
  public InvalidQuestionRequestException() {
    super("petId는 필수 양의 정수 쿼리입니다.");
  }
}
