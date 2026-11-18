package com.pawmento.media.application.port.out;

public interface CheckMediaPetAccessPort {
  void requireWriteAccess(Long memberId, Long petId);
}
