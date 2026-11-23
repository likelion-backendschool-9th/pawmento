package com.pawmento.question.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public final class QuestionNotFoundException extends ResourceNotFoundException {
  public QuestionNotFoundException() {
    super("질문을 찾을 수 없어요.");
  }
}
