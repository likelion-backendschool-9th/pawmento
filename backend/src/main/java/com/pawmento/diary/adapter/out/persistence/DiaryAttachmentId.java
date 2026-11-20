package com.pawmento.diary.adapter.out.persistence;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

@Embeddable
@Getter
class DiaryAttachmentId implements Serializable {
  @Column(name = "diary_id", nullable = false)
  private Long diaryId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  protected DiaryAttachmentId() {}

  DiaryAttachmentId(Long parentId, Long assetId) {
    this.diaryId = parentId;
    this.assetId = assetId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DiaryAttachmentId that
        && Objects.equals(diaryId, that.diaryId)
        && Objects.equals(assetId, that.assetId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(diaryId, assetId);
  }
}
