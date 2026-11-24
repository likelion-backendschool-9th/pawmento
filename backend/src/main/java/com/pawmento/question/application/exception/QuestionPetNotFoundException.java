package com.pawmento.question.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class QuestionPetNotFoundException extends ResourceNotFoundException {
  public QuestionPetNotFoundException() {
    super("반려동물을 찾을 수 없습니다.");
  }
}
