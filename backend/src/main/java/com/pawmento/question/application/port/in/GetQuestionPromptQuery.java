package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;

public record GetQuestionPromptQuery(Long memberId, Long petId, Long promptId, int page, int size) {
  public GetQuestionPromptQuery {
    if (memberId == null
        || memberId <= 0
        || petId == null
        || petId <= 0
        || promptId == null
        || promptId <= 0
        || page < 0
        || size < 1
        || size > 100) throw new InvalidQuestionAnswerException();
  }
}
