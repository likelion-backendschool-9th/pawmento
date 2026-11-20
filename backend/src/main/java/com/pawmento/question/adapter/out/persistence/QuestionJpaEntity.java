package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
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
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
    name = "question",
    uniqueConstraints = @UniqueConstraint(name = "uk_question_seed_key", columnNames = "seed_key"))
class QuestionJpaEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 100)
  private String seedKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuestionMode mode;

  @Column(nullable = false, length = Question.CONTENT_MAX_LENGTH)
  private String content;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected QuestionJpaEntity() {}

  Question toDomain() {
    try {
      return Question.restore(id, mode, content, enabled, createdAt, updatedAt, version);
    } catch (InvalidQuestionException cause) {
      throw new StoredQuestionInvalidException(cause);
    }
  }
}
