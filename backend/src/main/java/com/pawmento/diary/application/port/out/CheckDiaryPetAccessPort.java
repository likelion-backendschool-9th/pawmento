package com.pawmento.diary.application.port.out;

public interface CheckDiaryPetAccessPort {
  void requireAccess(Long memberId, Long petId);

  void requireLockedWriteAccess(Long memberId, Long petId);
}
