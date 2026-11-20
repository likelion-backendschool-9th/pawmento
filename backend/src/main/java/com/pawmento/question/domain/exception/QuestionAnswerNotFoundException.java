package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public final class QuestionAnswerNotFoundException extends ResourceNotFoundException {
  public QuestionAnswerNotFoundException() {
    super("해당 아이의 질문 또는 답변을 찾을 수 없어요.");
  }
}
