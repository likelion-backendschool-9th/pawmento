package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.VerifiedMedia;
import jakarta.persistence.*;
import lombok.*;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerifiedMediaColumns {
  @Column(length = 36, unique = true)
  private String storageKey;

  @Column(length = 100)
  private String mimeType;

  private Long sizeBytes;

  @Column(length = 64)
  private String serviceSha256;

  private Long durationMs;
  private Integer width;
  private Integer height;
  private Long thumbnailSizeBytes;

  static VerifiedMediaColumns from(VerifiedMedia v) {
    if (v == null) return null;
    var e = new VerifiedMediaColumns();
    e.storageKey = v.storageKey();
    e.mimeType = v.mimeType();
    e.sizeBytes = v.sizeBytes();
    e.serviceSha256 = v.sha256();
    e.durationMs = v.durationMs();
    e.width = v.width();
    e.height = v.height();
    e.thumbnailSizeBytes = v.thumbnailSizeBytes();
    return e;
  }

  VerifiedMedia toDomain() {
    return new VerifiedMedia(
        storageKey, mimeType, sizeBytes, serviceSha256, durationMs, width, height, thumbnailSizeBytes);
  }
}
