package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.*;
import com.pawmento.question.domain.exception.InvalidQuestionAnswerException;
import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(
    name = "question_answer",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_question_answer_author",
            columnNames = {"prompt_id", "author_member_id"}),
    indexes = @Index(name = "idx_question_answer_history", columnList = "pet_id,answered_at,id"))
class QuestionAnswerJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long petId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuestionMode mode;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "prompt_id",
      nullable = false,
      foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
  private QuestionPromptJpaEntity prompt;

  @Column(nullable = false)
  private Long authorMemberId;

  @Column(nullable = false)
  private String authorNameSnapshot;

  @Column(nullable = false, length = 3000)
  private String content;

  @Column(nullable = false)
  private Instant answeredAt;

  @Column(nullable = false)
  private LocalDate answeredDate;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected QuestionAnswerJpaEntity() {}

  static QuestionAnswerJpaEntity from(QuestionAnswer answer, QuestionPromptJpaEntity prompt) {
    var entity = new QuestionAnswerJpaEntity();
    entity.prompt = prompt;
    entity.petId = answer.getPrompt().getPetId();
    entity.mode = answer.getPrompt().getMode();
    entity.authorMemberId = answer.getAuthorMemberId();
    entity.authorNameSnapshot = answer.getAuthorNameSnapshot();
    entity.answeredAt = answer.getAnsweredAt();
    entity.answeredDate = answer.getAnsweredDate();
    entity.update(answer);
    return entity;
  }

  void update(QuestionAnswer answer) {
    content = answer.getContent();
    updatedAt = answer.getUpdatedAt();
  }

  QuestionAnswer toDomain() {
    try {
      var assigned = prompt.toDomain();
      if (!assigned.getPetId().equals(petId) || assigned.getMode() != mode)
        throw new InvalidQuestionAnswerException();
      return QuestionAnswer.restore(
          id,
          assigned,
          authorMemberId,
          authorNameSnapshot,
          content,
          answeredAt,
          answeredDate,
          updatedAt,
          version);
    } catch (InvalidQuestionAnswerException e) {
      throw new StoredQuestionInvalidException(e);
    }
  }
}
