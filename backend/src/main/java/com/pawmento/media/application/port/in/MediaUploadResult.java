package com.pawmento.media.application.port.in;

import com.pawmento.media.domain.MediaAsset;
import java.time.Instant;

public record MediaUploadResult(
    Long assetId,
    Long petId,
    String purpose,
    String type,
    String filename,
    String declaredMimeType,
    long declaredSizeBytes,
    String status,
    Instant expiresAt,
    String failureReason,
    String mimeType,
    Long sizeBytes,
    Long durationMs,
    Integer width,
    Integer height,
    boolean thumbnailAvailable) {
  public static MediaUploadResult from(MediaAsset a) {
    return new MediaUploadResult(
        a.getId(),
        a.getPetId(),
        a.getPurpose().name(),
        a.getMediaType().name(),
        a.getOriginalFilename(),
        a.getDeclaredMimeType(),
        a.getDeclaredSizeBytes(),
        a.getStatus().name(),
        a.getExpiresAt(),
        a.getFailureReason(),
        a.getVerified() == null ? null : a.getVerified().mimeType(),
        a.getVerified() == null ? null : a.getVerified().sizeBytes(),
        a.getVerified() == null ? null : a.getVerified().durationMs(),
        a.getVerified() == null ? null : a.getVerified().width(),
        a.getVerified() == null ? null : a.getVerified().height(),
        a.getVerified() != null && a.getVerified().thumbnailSizeBytes() != null);
  }
}
