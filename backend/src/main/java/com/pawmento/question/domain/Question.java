package com.pawmento.question.domain;

import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Instant;
import lombok.Getter;

/** 질문 풀의 원본. 아이에게 배정한 질문 및 개인 답변과는 별개다. */
@Getter
public final class Question {
  public static final int CONTENT_MAX_LENGTH = 300;
  private final Long id;
  private final QuestionMode mode;
  private final String content;
  private final boolean enabled;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final long version;

  private Question(
      Long id,
      QuestionMode mode,
      String content,
      boolean enabled,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    if (mode == null
        || content == null
        || content.isBlank()
        || content.indexOf('\0') >= 0
        || content.strip().length() > CONTENT_MAX_LENGTH
        || createdAt == null
        || updatedAt == null
        || updatedAt.isBefore(createdAt)
        || version < 0) {
      throw new InvalidQuestionException();
    }
    this.id = id;
    this.mode = mode;
    this.content = content.strip();
    this.enabled = enabled;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  public static Question create(QuestionMode mode, String content, Instant now) {
    return new Question(null, mode, content, true, now, now, 0);
  }

  public static Question restore(
      Long id,
      QuestionMode mode,
      String content,
      boolean enabled,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    if (id == null || id <= 0) {
      throw new InvalidQuestionException();
    }
    return new Question(id, mode, content, enabled, createdAt, updatedAt, version);
  }
}
