package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

public final class QuestionAnswerForbiddenException extends PermissionDeniedException {
  public QuestionAnswerForbiddenException() {
    super("본인이 작성한 답변만 변경할 수 있어요.");
  }
}
