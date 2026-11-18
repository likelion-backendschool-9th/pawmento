package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.GetDiaryEntryResult;
import java.time.*;

public record DiaryEntryResponse(
    Long recordId,
    Long petId,
    Long createdBy,
    String content,
    LocalDate recordedDate,
    Instant createdAt,
    Instant updatedAt,
    Long version) {
  static DiaryEntryResponse from(GetDiaryEntryResult r) {
    return new DiaryEntryResponse(
        r.recordId(),
        r.petId(),
        r.createdBy(),
        r.content(),
        r.recordedDate(),
        r.createdAt(),
        r.updatedAt(),
        r.version());
  }
}
