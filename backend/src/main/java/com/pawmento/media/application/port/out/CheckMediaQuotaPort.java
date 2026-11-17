package com.pawmento.media.application.port.out;

public interface CheckMediaQuotaPort {
  void lockMember(Long memberId);

  long countOpenUploads(Long memberId, java.time.Instant now);
}
