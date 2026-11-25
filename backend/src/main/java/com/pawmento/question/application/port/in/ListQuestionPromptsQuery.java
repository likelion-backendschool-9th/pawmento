package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;

public record ListQuestionPromptsQuery(
    Long memberId, Long petId, QuestionMode mode, int page, int size) {
  public ListQuestionPromptsQuery {
    if (memberId == null
        || memberId <= 0
        || petId == null
        || petId <= 0
        || page < 0
        || size < 1
        || size > 100) throw new InvalidQuestionAnswerException();
  }

  public static ListQuestionPromptsQuery from(
      Long member, Long pet, String mode, int page, int size) {
    if (mode != null && !java.util.Set.of("ACTIVE", "GARDEN").contains(mode))
      throw new InvalidQuestionAnswerException();
    return new ListQuestionPromptsQuery(
        member, pet, mode == null ? null : QuestionMode.valueOf(mode), page, size);
  }
}
