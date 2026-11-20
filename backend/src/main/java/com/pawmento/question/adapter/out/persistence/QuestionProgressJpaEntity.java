package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.QuestionProgress;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "question_progress")
class QuestionProgressJpaEntity {
  @EmbeddedId QuestionProgressId id;
  private Long currentPromptId;

  @Column(nullable = false)
  private long nextSequence;

  @Version private long version;

  protected QuestionProgressJpaEntity() {}

  static QuestionProgressJpaEntity from(QuestionProgress progress) {
    var entity = new QuestionProgressJpaEntity();
    entity.id = new QuestionProgressId(progress.getPetId(), progress.getMode());
    entity.update(progress);
    return entity;
  }

  void update(QuestionProgress progress) {
    currentPromptId = progress.getCurrentPromptId();
    nextSequence = progress.getNextSequence();
  }

  QuestionProgress toDomain() {
    try {
      return QuestionProgress.restore(id.petId, id.mode, currentPromptId, nextSequence);
    } catch (InvalidQuestionException e) {
      throw new StoredQuestionInvalidException(e);
    }
  }
}
