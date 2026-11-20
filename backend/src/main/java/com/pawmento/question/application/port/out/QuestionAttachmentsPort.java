package com.pawmento.question.application.port.out;

import java.util.*;

public interface QuestionAttachmentsPort {
  Map<Long, List<Long>> findAll(List<Long> answerIds);

  void replace(Long answerId, List<Long> ids);

  boolean existsByAssetId(Long assetId);

  default List<Long> find(Long id) {
    return findAll(List.of(id)).getOrDefault(id, List.of());
  }
}
