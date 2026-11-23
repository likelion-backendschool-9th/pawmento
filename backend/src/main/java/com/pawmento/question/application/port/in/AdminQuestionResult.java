package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.Question;
import java.time.Instant;

public record AdminQuestionResult(Long questionId, String mode, String content, boolean enabled,
    Instant createdAt, Instant updatedAt, long version) {
  public static AdminQuestionResult from(Question q) {
    return new AdminQuestionResult(q.getId(), q.getMode().name(), q.getContent(), q.isEnabled(),
        q.getCreatedAt(), q.getUpdatedAt(), q.getVersion());
  }
}
