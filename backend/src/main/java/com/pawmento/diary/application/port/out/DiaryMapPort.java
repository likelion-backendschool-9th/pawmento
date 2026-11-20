package com.pawmento.diary.application.port.out;

import com.pawmento.diary.domain.DiaryLocationIntent;
import java.util.Optional;

public interface DiaryMapPort {
  enum Result {
    APPLIED,
    DUPLICATE,
    STALE
  }

  Optional<DiaryMapSnapshot> find(Long diaryId);

  Optional<String> resolveAddress(double latitude, double longitude);

  Result apply(Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationIntent intent);

  Result reconcile(
      Long diaryId,
      Long petId,
      Long createdBy,
      long revision,
      DiaryMapSnapshot expected,
      DiaryLocationIntent intent);
}
