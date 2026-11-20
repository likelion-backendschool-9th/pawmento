package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public final class QuestionAnswerConflictException extends StateConflictException {
  public QuestionAnswerConflictException() {
    super("이미 답변했거나 답변·모드가 변경됐어요. 새로고침해 주세요.");
  }
}
