package com.pawmento.media.application.port.out;

public interface CollectMediaOrphansPort {
  void collect(java.time.Instant cutoff);
}
