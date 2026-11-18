package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.VerifiedMedia;
import jakarta.persistence.*;
import lombok.*;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerifiedMediaColumns {
  @Column(name = "storage_key", length = 36, unique = true)
  private String key;

  @Column(name = "mime_type", length = 100)
  private String mime;

  @Column(name = "size_bytes")
  private Long size;

  @Column(name = "service_sha256", length = 64)
  private String checksum;

  private Long durationMs;
  private Integer width;
  private Integer height;
  private Long thumbnailSizeBytes;

  static VerifiedMediaColumns from(VerifiedMedia v) {
    if (v == null) return null;
    var e = new VerifiedMediaColumns();
    e.key = v.storageKey();
    e.mime = v.mimeType();
    e.size = v.sizeBytes();
    e.checksum = v.sha256();
    e.durationMs = v.durationMs();
    e.width = v.width();
    e.height = v.height();
    e.thumbnailSizeBytes = v.thumbnailSizeBytes();
    return e;
  }

  VerifiedMedia toDomain() {
    return new VerifiedMedia(
        key, mime, size, checksum, durationMs, width, height, thumbnailSizeBytes);
  }
}
