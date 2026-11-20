package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.exception.InvalidDiaryEntryException;
import java.util.HashSet;
import java.util.List;

public record DiaryWriteFeatures(List<Long> attachmentIds, DiaryLocationChange location) {
  public DiaryWriteFeatures(List<Long> attachmentIds, boolean locationRequested) {
    this(
        attachmentIds, locationRequested ? DiaryLocationChange.auto() : DiaryLocationChange.keep());
  }

  public DiaryWriteFeatures {
    java.util.Objects.requireNonNull(location, "위치 변경 의도가 필요합니다.");
    if (attachmentIds != null) {
      if (attachmentIds.size() > 5
          || attachmentIds.stream().anyMatch(id -> id == null || id <= 0)
          || new HashSet<>(attachmentIds).size() != attachmentIds.size())
        throw new InvalidDiaryEntryException();
      attachmentIds = List.copyOf(attachmentIds);
    }
  }

  public static DiaryWriteFeatures textOnly() {
    return new DiaryWriteFeatures(null, false);
  }
}
