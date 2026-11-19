package com.pawmento.diary.application.port.out;

import java.util.*;

public interface DiaryAttachmentsPort {
  Map<Long, List<Long>> findAll(List<Long> recordIds);

  void replace(Long recordId, List<Long> assetIds);

  boolean existsByAssetId(Long assetId);

  default List<Long> find(Long recordId) {
    return findAll(List.of(recordId)).getOrDefault(recordId, List.of());
  }
}
