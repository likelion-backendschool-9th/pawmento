package com.pawmento.media.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.*;
import lombok.*;

@Entity
@Table(name = "media_validation_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaValidationJobJpaEntity {
  @Id
  private Long assetId;

  @Column(nullable = false)
  private Instant requestedAt;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(length = 16)
  private String kind;

  @Column(length = 36)
  private String token;

  private Instant leaseUntil;
  private Instant nextAttemptAt;

  @Column(columnDefinition = "integer default 0")
  private int attempts;

  @Column(length = 100)
  private String lastError;

  public static MediaValidationJobJpaEntity from(com.pawmento.media.domain.MediaJob job) {
    var e = new MediaValidationJobJpaEntity();
    e.copy(job);
    return e;
  }

  public void copy(com.pawmento.media.domain.MediaJob job) {
    assetId = job.getAssetId();
    requestedAt = job.getRequestedAt();
    status = job.getStatus().name();
    kind = job.getKind().name();
    token = job.getToken();
    leaseUntil = job.getLeaseUntil();
    nextAttemptAt = job.getNextAttemptAt();
    attempts = job.getAttempts();
    lastError = job.getLastError();
  }

  public com.pawmento.media.domain.MediaJob toDomain() {
    return com.pawmento.media.domain.MediaJob.restore(
        assetId, requestedAt, status, kind, token, leaseUntil, nextAttemptAt, attempts, lastError);
  }
}
