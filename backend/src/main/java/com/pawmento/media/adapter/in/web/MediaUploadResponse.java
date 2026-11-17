package com.pawmento.media.adapter.in.web;

import com.pawmento.global.web.ApiPaths;
import com.pawmento.media.application.port.in.MediaUploadResult;
import java.time.Instant;

public record MediaUploadResponse(
    Long assetId,
    Long petId,
    String purpose,
    String type,
    String filename,
    String declaredMimeType,
    long declaredSizeBytes,
    String status,
    Instant expiresAt,
    String contentPath,
    String statusPath,
    String failureReason) {
  public static MediaUploadResponse from(MediaUploadResult r) {
    String path = ApiPaths.V1 + "/pets/" + r.petId() + "/media/" + r.assetId();
    return new MediaUploadResponse(
        r.assetId(),
        r.petId(),
        r.purpose(),
        r.type(),
        r.filename(),
        r.declaredMimeType(),
        r.declaredSizeBytes(),
        r.status(),
        r.expiresAt(),
        path + "/content",
        path,
        r.failureReason());
  }
}
