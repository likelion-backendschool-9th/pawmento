package com.pawmento.diary.application.port.out;

import java.util.*;

public interface DiaryAttachmentsPort {
  Map<Long, List<Long>> findAll(List<Long> diaryIds);

  void replace(Long diaryId, List<Long> assetIds);

  boolean existsByAssetId(Long assetId);

  default List<Long> find(Long diaryId) {
    return findAll(List.of(diaryId)).getOrDefault(diaryId, List.of());
  }
}
