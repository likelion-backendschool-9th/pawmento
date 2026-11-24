package com.pawmento.diary.adapter.out.access;

import com.pawmento.diary.application.exception.*;
import com.pawmento.diary.application.port.out.CheckDiaryPetAccessPort;
import com.pawmento.family.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FamilyDiaryPetAccessAdapter implements CheckDiaryPetAccessPort {
  private final CheckPetAccessUseCase family;
  private final GetPetOwnerUseCase owners;
  private final LockPetOwnerUseCase locks;

  @Override
  public void requireAccess(Long memberId, Long petId) {
    owners.get(new GetPetOwnerQuery(petId)).orElseThrow(DiaryPetNotFoundException::new);
    requireFamily(memberId, petId);
  }

  @Override
  public void requireLockedWriteAccess(Long memberId, Long petId) {
    locks.lock(new LockPetOwnerQuery(petId)).orElseThrow(DiaryPetNotFoundException::new);
    requireFamily(memberId, petId);
  }

  private void requireFamily(Long memberId, Long petId) {
    if (!family.check(new CheckPetAccessQuery(petId, memberId)).canRecord())
      throw new DiaryPetAccessDeniedException();
  }
}
