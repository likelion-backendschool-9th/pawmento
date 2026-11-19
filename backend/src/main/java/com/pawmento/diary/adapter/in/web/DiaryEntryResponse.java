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
    Long version,
    java.util.List<AttachmentResponse> attachments) {
  public record AttachmentResponse(
      Long assetId,
      String type,
      String mimeType,
      long sizeBytes,
      Long durationMs,
      Integer width,
      Integer height,
      String contentUrl,
      String thumbnailUrl) {}

  static DiaryEntryResponse from(GetDiaryEntryResult r) {
    return new DiaryEntryResponse(
        r.recordId(),
        r.petId(),
        r.createdBy(),
        r.content(),
        r.recordedDate(),
        r.createdAt(),
        r.updatedAt(),
        r.version(),
        r.attachments().stream()
            .map(
                a -> {
                  String path = "/api/v1/diaries/" + r.recordId() + "/attachments/" + a.assetId();
                  return new AttachmentResponse(
                      a.assetId(),
                      a.type(),
                      a.mimeType(),
                      a.sizeBytes(),
                      a.durationMs(),
                      a.width(),
                      a.height(),
                      path + "?petId=" + r.petId(),
                      a.thumbnail() ? path + "/thumbnail?petId=" + r.petId() : null);
                })
            .toList());
  }
}
