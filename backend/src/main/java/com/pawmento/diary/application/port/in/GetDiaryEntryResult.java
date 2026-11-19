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
    Long version,
    java.util.List<DiaryAttachment> attachments) {
  public GetDiaryEntryResult {
    attachments = java.util.List.copyOf(attachments);
  }

  public static GetDiaryEntryResult from(
      DiaryEntry e, java.util.List<DiaryAttachment> attachments) {
    return new GetDiaryEntryResult(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion(),
        attachments);
  }

  public static GetDiaryEntryResult from(DiaryEntry e) {
    return new GetDiaryEntryResult(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion(),
        java.util.List.of());
  }
}
