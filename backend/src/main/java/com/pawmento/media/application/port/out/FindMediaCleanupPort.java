package com.pawmento.media.application.port.out;

public interface FindMediaCleanupPort {
  java.util.List<Long> candidates(java.time.Instant now, int limit);
}
