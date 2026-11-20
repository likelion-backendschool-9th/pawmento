package com.pawmento.question.domain;

import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Instant;
import lombok.Getter;

@Getter
public final class QuestionPrompt {
  private final Long id;
  private final Long petId;
  private final QuestionMode mode;
  private final Long questionId;
  private final String questionSnapshot;
  private final long sequence;
  private final Instant assignedAt;
  private Instant completedAt;

  private QuestionPrompt(
      Long id,
      Long petId,
      QuestionMode mode,
      Long questionId,
      String snapshot,
      long sequence,
      Instant assignedAt) {
    if (petId == null || petId <= 0 || questionId == null || questionId <= 0 || sequence < 1) {
      throw new InvalidQuestionException();
    }
    Question validated = Question.create(mode, snapshot, assignedAt);
    this.id = id;
    this.petId = petId;
    this.mode = mode;
    this.questionId = questionId;
    this.questionSnapshot = validated.getContent();
    this.sequence = sequence;
    this.assignedAt = assignedAt;
  }

  public void complete(Instant now) {
    if (now == null) throw new InvalidQuestionException();
    if (completedAt == null) completedAt = now;
  }

  public static QuestionPrompt restore(
      Long id,
      Long petId,
      QuestionMode mode,
      Long questionId,
      String snapshot,
      long sequence,
      Instant assignedAt,
      Instant completedAt) {
    var prompt = restore(id, petId, mode, questionId, snapshot, sequence, assignedAt);
    prompt.completedAt = completedAt;
    return prompt;
  }

  public static QuestionPrompt assign(
      Long petId, QuestionMode mode, Question question, long sequence, Instant now) {
    if (question == null || question.getMode() != mode || !question.isEnabled())
      throw new IllegalStateException("배정할 수 없는 질문입니다.");
    return new QuestionPrompt(
        null, petId, mode, question.getId(), question.getContent(), sequence, now);
  }

  public static QuestionPrompt restore(
      Long id,
      Long petId,
      QuestionMode mode,
      Long questionId,
      String snapshot,
      long sequence,
      Instant assignedAt) {
    if (id == null || id <= 0) throw new InvalidQuestionException();
    return new QuestionPrompt(id, petId, mode, questionId, snapshot, sequence, assignedAt);
  }
}
