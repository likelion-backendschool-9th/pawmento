package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.exception.InvalidQuestionException;

public record GetAdminQuestionQuery(Long memberId, Long questionId) {
  public GetAdminQuestionQuery {
    if (questionId == null || questionId <= 0) throw new InvalidQuestionException();
  }
}
