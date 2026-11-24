package com.pawmento.question.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public class QuestionPetAccessDeniedException extends PermissionDeniedException {
  public QuestionPetAccessDeniedException() {
    super("권한이 없습니다.");
  }
}
