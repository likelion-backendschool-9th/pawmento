package com.pawmento.media.application.port.out;

public interface EnqueueMediaValidationPort {
  void enqueue(Long assetId, java.time.Instant requestedAt);
}
