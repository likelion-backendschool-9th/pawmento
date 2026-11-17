package com.pawmento.media.adapter.out.persistence;

import com.pawmento.media.domain.MediaWork;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import lombok.*;

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

  public static MediaValidationJobJpaEntity pending(Long id, Instant now) {
    var e = new MediaValidationJobJpaEntity();
    e.assetId = id;
    e.requestedAt = now;
    e.status = "PENDING";
    e.kind = "VALIDATE";
    e.nextAttemptAt = now;
    return e;
  }

  public void deletion(Instant now) {
    kind = "DELETE";
    status = "PENDING";
    attempts = 0;
    token = null;
    leaseUntil = null;
    nextAttemptAt = now;
    lastError = null;
  }

  public MediaWork claim(Instant now) {
    token = UUID.randomUUID().toString();
    attempts++;
    status = "RUNNING";
    leaseUntil = now.plusSeconds(600);
    if (kind == null) kind = "VALIDATE";
    return new MediaWork(assetId, token, attempts, kind);
  }

  public boolean owns(MediaWork w) {
    return java.util.Objects.equals(token, w.token()) && kind.equals(w.kind());
  }

  public void complete() {
    status = "DONE";
    leaseUntil = null;
    lastError = null;
  }

  public void retry(Instant now, String error) {
    status = "RETRY";
    leaseUntil = null;
    lastError = error;
    nextAttemptAt = now.plusSeconds(Math.min(3600, 5L << Math.min(attempts, 9)));
  }
}
