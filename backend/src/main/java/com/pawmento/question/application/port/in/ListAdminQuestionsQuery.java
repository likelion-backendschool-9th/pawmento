package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.QuestionFilter;
import com.pawmento.question.domain.QuestionMode;
import java.util.Objects;

public record ListAdminQuestionsQuery(Long memberId, QuestionFilter filter) {
  public ListAdminQuestionsQuery {
    Objects.requireNonNull(filter);
  }

  public static ListAdminQuestionsQuery from(Long member, String mode, Boolean enabled,
      String search, int page, int size) {
    return new ListAdminQuestionsQuery(member, new QuestionFilter(
        mode == null ? null : QuestionMode.valueOf(mode), enabled, search, page, size));
  }
}
