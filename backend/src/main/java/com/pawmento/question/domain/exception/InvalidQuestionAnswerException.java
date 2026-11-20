package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidQuestionAnswerException extends RuleViolationException {
  public InvalidQuestionAnswerException() {
    super("답변 본문·작성자·등록 시각·버전을 확인해 주세요.");
  }
}
