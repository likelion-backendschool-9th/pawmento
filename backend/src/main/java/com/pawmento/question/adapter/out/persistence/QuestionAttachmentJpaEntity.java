package com.pawmento.question.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "question_answer_attachment")
@Getter
class QuestionAttachmentJpaEntity {
  @EmbeddedId private QuestionAttachmentId id;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected QuestionAttachmentJpaEntity() {}

  static QuestionAttachmentJpaEntity from(Long parentId, Long assetId, int order) {
    var row = new QuestionAttachmentJpaEntity();
    row.id = new QuestionAttachmentId(parentId, assetId);
    row.sortOrder = order;
    return row;
  }
}
