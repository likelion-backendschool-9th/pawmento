package com.pawmento.media.domain;

import com.pawmento.media.domain.exception.MediaUploadLimitException;
import java.time.Duration;

public record MediaPolicy(
    Duration sessionTtl,
    Duration unattachedTtl,
    Duration uploadTimeout,
    Duration workLease,
    int maxOpenUploads) {
  public MediaPolicy {
    for (Duration value : new Duration[] {sessionTtl, unattachedTtl, uploadTimeout, workLease})
      if (value == null || value.isNegative() || value.isZero())
        throw new IllegalArgumentException("Positive media lifetime required");
    if (maxOpenUploads < 1) throw new IllegalArgumentException("Positive upload limit required");
  }

  public static MediaPolicy defaults() {
    return new MediaPolicy(
        Duration.ofMinutes(10),
        Duration.ofHours(24),
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        5);
  }

  public void requireUploadSlot(long count) {
    if (count < 0) throw new IllegalArgumentException("Negative upload count");
    if (count >= maxOpenUploads) throw new MediaUploadLimitException(maxOpenUploads);
  }

  public Duration retryDelay(int attempts) {
    return Duration.ofSeconds(Math.min(3600, 5L << Math.min(attempts, 9)));
  }
}
