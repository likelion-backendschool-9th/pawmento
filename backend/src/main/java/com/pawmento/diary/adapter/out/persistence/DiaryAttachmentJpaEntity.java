package com.pawmento.diary.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "diary_attachments")
@Getter
class DiaryAttachmentJpaEntity {
  @EmbeddedId private DiaryAttachmentId id;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected DiaryAttachmentJpaEntity() {}

  static DiaryAttachmentJpaEntity from(Long parentId, Long assetId, int order) {
    var row = new DiaryAttachmentJpaEntity();
    row.id = new DiaryAttachmentId(parentId, assetId);
    row.sortOrder = order;
    return row;
  }
}
