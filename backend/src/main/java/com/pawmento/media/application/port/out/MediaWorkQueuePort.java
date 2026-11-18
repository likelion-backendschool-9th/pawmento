package com.pawmento.media.application.port.out;

public interface MediaWorkQueuePort {
  java.util.Optional<com.pawmento.media.domain.MediaWork> claim(java.time.Instant now);

  boolean owns(com.pawmento.media.domain.MediaWork work);

  void complete(com.pawmento.media.domain.MediaWork work);

  void fail(
      com.pawmento.media.domain.MediaWork work,
      java.time.Instant now,
      String error,
      boolean retryable);

  void enqueueDeletion(Long assetId, java.time.Instant now);
}
