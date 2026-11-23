package com.pawmento.question.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public final class QuestionAdminDeniedException extends PermissionDeniedException {
  public QuestionAdminDeniedException() {
    super("관리자만 질문 원본을 조회할 수 있어요.");
  }
}
