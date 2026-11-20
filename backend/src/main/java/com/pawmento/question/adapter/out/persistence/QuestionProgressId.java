package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.domain.QuestionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class QuestionProgressId implements Serializable {
  @Column(nullable = false)
  Long petId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  QuestionMode mode;

  protected QuestionProgressId() {}

  QuestionProgressId(Long petId, QuestionMode mode) {
    this.petId = petId;
    this.mode = mode;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof QuestionProgressId that
        && Objects.equals(petId, that.petId)
        && mode == that.mode;
  }

  @Override
  public int hashCode() {
    return Objects.hash(petId, mode);
  }
}
