package com.pawmento.question.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class QuestionModificationConflictException extends StateConflictException {
  public QuestionModificationConflictException() {
    super("질문이 변경됐어요. 최신 질문을 확인한 뒤 다시 시도해 주세요.");
  }
}
