package com.pawmento.media.domain;

import com.pawmento.media.domain.exception.InvalidMediaStateException;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

@Getter
public class MediaJob {
  public enum Status {
    PENDING,
    RUNNING,
    RETRY,
    DONE,
    FAILED
  }

  public enum Kind {
    VALIDATE,
    DELETE
  }

  private Long assetId;
  private Instant requestedAt;
  private Status status;
  private Kind kind;
  private String token;
  private Instant leaseUntil;
  private Instant nextAttemptAt;
  private int attempts;
  private String lastError;

  private MediaJob() {}

  public static MediaJob pending(Long id, Instant now) {
    return restore(id, now, "PENDING", "VALIDATE", null, null, now, 0, null);
  }

  public static MediaJob restore(
      Long id,
      Instant requested,
      String status,
      String kind,
      String token,
      Instant lease,
      Instant next,
      int attempts,
      String error) {
    var j = new MediaJob();
    j.assetId = id;
    j.requestedAt = requested;
    try {
      j.status = Status.valueOf(status);
      j.kind = kind == null ? Kind.VALIDATE : Kind.valueOf(kind);
    } catch (RuntimeException e) {
      throw new InvalidMediaStateException();
    }
    if (id == null
        || id <= 0
        || requested == null
        || attempts < 0
        || (j.status == Status.RUNNING && (token == null || lease == null)))
      throw new InvalidMediaStateException();
    j.token = token;
    j.leaseUntil = lease;
    j.nextAttemptAt = next == null ? requested : next;
    j.attempts = attempts;
    j.lastError = error;
    return j;
  }

  public void scheduleDeletion(Instant now) {
    kind = Kind.DELETE;
    status = Status.PENDING;
    attempts = 0;
    token = null;
    leaseUntil = null;
    nextAttemptAt = now;
    lastError = null;
  }

  public MediaWork claim(Instant now, String token, MediaPolicy policy) {
    boolean due =
        ((status == Status.PENDING || status == Status.RETRY) && !now.isBefore(nextAttemptAt))
            || (status == Status.RUNNING && !now.isBefore(leaseUntil));
    if (!due || token == null || !token.matches("[0-9a-f-]{36}"))
      throw new InvalidMediaStateException();
    this.token = token;
    attempts++;
    status = Status.RUNNING;
    leaseUntil = now.plus(policy.workLease());
    return new MediaWork(assetId, token, attempts, kind.name());
  }

  public boolean owns(MediaWork w) {
    return status == Status.RUNNING
        && Objects.equals(assetId, w.assetId())
        && Objects.equals(token, w.token())
        && kind.name().equals(w.kind());
  }

  public void complete() {
    status = Status.DONE;
    leaseUntil = null;
    lastError = null;
  }

  public void fail(Instant now, String error, boolean retryable, MediaPolicy policy) {
    status = retryable ? Status.RETRY : Status.FAILED;
    leaseUntil = null;
    lastError = error;
    nextAttemptAt = now.plus(policy.retryDelay(attempts));
  }
}
