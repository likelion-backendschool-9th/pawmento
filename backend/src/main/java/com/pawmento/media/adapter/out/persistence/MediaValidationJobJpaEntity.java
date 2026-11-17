package com.pawmento.media.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 검증 작업자가 구현되기 전에도 수신한 파일의 처리 의도를 DB에 남긴다.
@Entity
@Table(name = "media_validation_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaValidationJobJpaEntity {
  @Id
  @Column(name = "asset_id")
  private Long assetId;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(nullable = false, length = 20)
  private String status;

  public static MediaValidationJobJpaEntity pending(Long id, Instant now) {
    var e = new MediaValidationJobJpaEntity();
    e.assetId = id;
    e.requestedAt = now;
    e.status = "PENDING";
    return e;
  }
}
