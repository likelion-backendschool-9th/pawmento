package com.pawmento.question.domain;

import com.pawmento.question.domain.exception.InvalidQuestionException;
import com.pawmento.question.domain.exception.QuestionProgressInconsistentException;
import java.util.List;
import lombok.Getter;

@Getter
public final class QuestionProgress {
  private final Long petId;
  private final QuestionMode mode;
  private Long currentPromptId;
  private long nextSequence;

  private QuestionProgress(Long petId, QuestionMode mode, Long currentPromptId, long nextSequence) {
    if (petId == null
        || petId <= 0
        || mode == null
        || nextSequence < 1
        || (currentPromptId != null && currentPromptId <= 0)) throw new InvalidQuestionException();
    this.petId = petId;
    this.mode = mode;
    this.currentPromptId = currentPromptId;
    this.nextSequence = nextSequence;
  }

  public static QuestionProgress create(Long petId, QuestionMode mode) {
    return new QuestionProgress(petId, mode, null, 1);
  }

  public static QuestionProgress restore(
      Long petId, QuestionMode mode, Long currentPromptId, long nextSequence) {
    return new QuestionProgress(petId, mode, currentPromptId, nextSequence);
  }

  public List<Question> candidates(List<Question> questions, Long previousQuestionId) {
    var active = questions.stream().filter(q -> q.isEnabled() && q.getMode() == mode).toList();
    var different = active.stream().filter(q -> !q.getId().equals(previousQuestionId)).toList();
    return different.isEmpty() ? active : different;
  }

  public void assigned(Long promptId) {
    if (currentPromptId != null || promptId == null || promptId <= 0)
      throw new IllegalStateException("현재 질문을 중복 배정할 수 없습니다.");
    long next = Math.addExact(nextSequence, 1);
    currentPromptId = promptId;
    nextSequence = next;
  }

  public void completeCurrent(QuestionPrompt prompt) {
    requireCurrent(prompt);
    currentPromptId = null;
  }

  public void requireCurrent(QuestionPrompt prompt) {
    if (prompt == null
        || !petId.equals(prompt.getPetId())
        || mode != prompt.getMode()
        || !prompt.getId().equals(currentPromptId))
      throw new QuestionProgressInconsistentException();
  }
}
