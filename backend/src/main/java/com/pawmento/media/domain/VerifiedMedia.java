package com.pawmento.media.domain;

import com.pawmento.media.domain.exception.InvalidVerifiedMediaException;

public record VerifiedMedia(
    String storageKey,
    String mimeType,
    long sizeBytes,
    String sha256,
    Long durationMs,
    Integer width,
    Integer height,
    Long thumbnailSizeBytes) {
  public VerifiedMedia {
    if (storageKey == null
        || !storageKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        || sizeBytes <= 0
        || sizeBytes > MediaAsset.MAX_FILE_BYTES
        || sha256 == null
        || !sha256.matches("[0-9a-f]{64}"))
      throw new InvalidVerifiedMediaException(InvalidVerifiedMediaException.Reason.FILE);
    boolean image = "image/jpeg".equals(mimeType) || "image/png".equals(mimeType);
    if (image) {
      if (width == null
          || height == null
          || width <= 0
          || height <= 0
          || (long) width * height > 40000000
          || durationMs != null)
        throw new InvalidVerifiedMediaException(InvalidVerifiedMediaException.Reason.IMAGE);
    } else if (!"audio/mp4".equals(mimeType)
        || durationMs == null
        || durationMs <= 0
        || durationMs > 300000
        || width != null
        || height != null
        || thumbnailSizeBytes != null) {
      throw new InvalidVerifiedMediaException(InvalidVerifiedMediaException.Reason.AUDIO);
    }
    if (thumbnailSizeBytes != null && (thumbnailSizeBytes <= 0 || thumbnailSizeBytes > 1024 * 1024))
      throw new InvalidVerifiedMediaException(InvalidVerifiedMediaException.Reason.THUMBNAIL);
  }
}
