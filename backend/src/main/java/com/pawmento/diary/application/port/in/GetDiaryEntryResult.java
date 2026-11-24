package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryEntry;
import java.time.*;

public record GetDiaryEntryResult(
    Long diaryId,
    Long petId,
    Long createdBy,
    Author author,
    String content,
    LocalDate recordedDate,
    Instant createdAt,
    Instant updatedAt,
    Long version,
    java.util.List<DiaryAttachment> attachments,
    Location location) {
  public record Author(Long memberId, String displayName) {}

  public record Location(String mode, Double latitude, Double longitude, String placeName) {}

  public GetDiaryEntryResult {
    attachments = java.util.List.copyOf(attachments);
  }

  public static GetDiaryEntryResult from(
      DiaryEntry e, java.util.List<DiaryAttachment> attachments, Long requester) {
    var original = e.getLocationIntent();
    Location location =
        original == null || !e.getCreatedBy().equals(requester)
            ? null
            : new Location(
                original.mode().name(),
                original.latitude(),
                original.longitude(),
                original.placeName());
    return new GetDiaryEntryResult(
        e.getId(),
        e.getPetId(),
        e.getCreatedBy(),
        new Author(
            e.getCreatedBy(),
            com.pawmento.diary.application.DiaryAuthorNames.stored(e.getAuthorNameSnapshot())),
        e.getContent(),
        e.getRecordedDate(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion(),
        attachments,
        location);
  }
}
