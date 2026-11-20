package com.pawmento.question.application.exception;

import com.pawmento.global.exception.StateConflictException;

public final class QuestionPoolEmptyException extends StateConflictException {
  public QuestionPoolEmptyException() {
    super("지금 배정할 질문이 없어요. 잠시 후 다시 시도해 주세요.");
  }
}
