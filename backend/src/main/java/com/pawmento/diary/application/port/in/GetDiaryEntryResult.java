package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryEntry;
import java.time.*;

public record GetDiaryEntryResult(
    Long recordId,
    Long petId,
    Long createdBy,
    String content,
    LocalDate recordedDate,
    Instant createdAt,
    Instant updatedAt,
    Long version) {
  public static GetDiaryEntryResult from(DiaryEntry e) {
    return new GetDiaryEntryResult(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion());
  }
}
