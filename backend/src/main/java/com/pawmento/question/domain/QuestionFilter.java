package com.pawmento.question.domain;

import com.pawmento.question.domain.exception.InvalidQuestionFilterException;

public record QuestionFilter(QuestionMode mode, Boolean enabled, String search, int page, int size) {
  public QuestionFilter {
    search = search == null ? "" : search.strip();
    if (search.length() > Question.CONTENT_MAX_LENGTH || search.indexOf('\0') >= 0 || page < 0 || size < 1 || size > 100)
      throw new InvalidQuestionFilterException();
  }
}
