package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.GetDiaryEntryResult;
import java.time.*;

public record DiaryEntryResponse(
    Long diaryId,
    Long petId,
    Long createdBy,
    GetDiaryEntryResult.Author author,
    String content,
    LocalDate recordedDate,
    Instant createdAt,
    Instant updatedAt,
    Long version,
    java.util.List<AttachmentResponse> attachments,
    GetDiaryEntryResult.Location location) {
  public record AttachmentResponse(
      Long assetId,
      String type,
      String mimeType,
      long sizeBytes,
      Long durationMs,
      Integer width,
      Integer height,
      String contentUrl,
      String thumbnailUrl,
      boolean available) {}

  static DiaryEntryResponse from(GetDiaryEntryResult r) {
    return new DiaryEntryResponse(
        r.diaryId(),
        r.petId(),
        r.createdBy(),
        r.author(),
        r.content(),
        r.recordedDate(),
        r.createdAt(),
        r.updatedAt(),
        r.version(),
        r.attachments().stream()
            .map(
                a -> {
                  String path = "/api/v1/diaries/" + r.diaryId() + "/attachments/" + a.assetId();
                  return new AttachmentResponse(
                      a.assetId(),
                      a.type(),
                      a.mimeType(),
                      a.sizeBytes(),
                      a.durationMs(),
                      a.width(),
                      a.height(),
                      a.available() ? path + "?petId=" + r.petId() : null,
                      a.available() && a.thumbnail()
                          ? path + "/thumbnail?petId=" + r.petId()
                          : null,
                      a.available());
                })
            .toList(),
        r.location());
  }
}
