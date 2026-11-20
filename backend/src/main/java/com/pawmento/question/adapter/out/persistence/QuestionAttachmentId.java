package com.pawmento.question.adapter.out.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

@Embeddable
@Getter
class QuestionAttachmentId implements Serializable {
  @Column(name = "answer_id", nullable = false)
  private Long answerId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  protected QuestionAttachmentId() {}

  QuestionAttachmentId(Long parentId, Long assetId) {
    this.answerId = parentId;
    this.assetId = assetId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof QuestionAttachmentId that
        && Objects.equals(answerId, that.answerId)
        && Objects.equals(assetId, that.assetId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(answerId, assetId);
  }
}
