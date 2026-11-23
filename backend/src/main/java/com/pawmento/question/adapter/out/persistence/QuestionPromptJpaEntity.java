package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionPrompt;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "question_prompt",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_question_prompt_sequence",
          columnNames = {"pet_id", "mode", "sequence"}),
      @UniqueConstraint(
          name = "uk_question_prompt_identity",
          columnNames = {"id", "pet_id", "mode"})
    })
class QuestionPromptJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long petId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuestionMode mode;

  @Column(nullable = false)
  private Long questionId;

  @Column(nullable = false, length = Question.CONTENT_MAX_LENGTH)
  private String questionSnapshot;

  @Column(nullable = false)
  private long sequence;

  @Column(nullable = false)
  private Instant assignedAt;

  private Instant completedAt;

  protected QuestionPromptJpaEntity() {}

  void complete(QuestionPrompt prompt) {
    completedAt = prompt.getCompletedAt();
  }

  static QuestionPromptJpaEntity from(QuestionPrompt prompt) {
    var entity = new QuestionPromptJpaEntity();
    entity.petId = prompt.getPetId();
    entity.mode = prompt.getMode();
    entity.questionId = prompt.getQuestionId();
    entity.questionSnapshot = prompt.getQuestionSnapshot();
    entity.sequence = prompt.getSequence();
    entity.assignedAt = prompt.getAssignedAt();
    return entity;
  }

  Long id() {
    return id;
  }

  Long questionId() {
    return questionId;
  }

  QuestionPrompt toDomain() {
    try {
      return QuestionPrompt.restore(
          id, petId, mode, questionId, questionSnapshot, sequence, assignedAt, completedAt);
    } catch (InvalidQuestionException e) {
      throw new StoredQuestionInvalidException(e);
    }
  }
}
