package com.pawmento.media.application.port.out;

public interface CheckMediaPetAccessPort {
  void requireWriteAccess(Long memberId, Long petId);

  void requireLockedWriteAccess(Long memberId, Long petId);

  void requireProfileOwner(Long memberId, Long petId);
}
