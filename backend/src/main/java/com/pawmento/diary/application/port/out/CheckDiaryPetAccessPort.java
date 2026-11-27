package com.pawmento.diary.application.port.out;

public interface CheckDiaryPetAccessPort {
  void requireAccess(Long memberId, Long petId);

  /** 현재 트랜잭션에서 pet을 잠근 뒤 가족 접근을 확인하고 소유주 여부를 반환한다. */
  boolean isOwnerForLockedDeletion(Long memberId, Long petId);

  void requireLockedWriteAccess(Long memberId, Long petId);
}
