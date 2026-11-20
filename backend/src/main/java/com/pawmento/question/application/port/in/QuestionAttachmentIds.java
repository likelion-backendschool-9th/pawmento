package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;
import java.util.*;

public final class QuestionAttachmentIds {
  private QuestionAttachmentIds() {}

  public static List<Long> copy(List<Long> ids) {
    if (ids == null) return null;
    if (ids.size() > 5
        || ids.stream().anyMatch(id -> id == null || id <= 0)
        || new HashSet<>(ids).size() != ids.size()) throw new InvalidQuestionAnswerException();
    return List.copyOf(ids);
  }
}
