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
    String failureReason) {
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
        a.getFailureReason());
  }
}
