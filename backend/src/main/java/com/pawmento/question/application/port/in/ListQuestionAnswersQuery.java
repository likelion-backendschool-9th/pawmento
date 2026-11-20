package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;
import java.time.LocalDate;

public record ListQuestionAnswersQuery(
    Long memberId,
    Long petId,
    QuestionMode mode,
    Long promptId,
    Long authorMemberId,
    LocalDate from,
    LocalDate to,
    int page,
    int size) {
  public ListQuestionAnswersQuery {
    if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)))
      throw new InvalidQuestionAnswerException();
  }

  public static ListQuestionAnswersQuery from(
      Long member,
      Long pet,
      String mode,
      Long prompt,
      Long author,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    return new ListQuestionAnswersQuery(
        member,
        pet,
        mode == null ? null : QuestionMode.valueOf(mode),
        prompt,
        author,
        from,
        to,
        page,
        size);
  }
}
