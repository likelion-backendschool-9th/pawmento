package com.pawmento.media.adapter.out.access;

import com.pawmento.family.application.port.in.*;
import com.pawmento.media.application.exception.*;
import com.pawmento.media.application.port.out.CheckMediaPetAccessPort;
import com.pawmento.pet.application.port.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FamilyMediaPetAccessAdapter implements CheckMediaPetAccessPort {
  private final CheckPetAccessUseCase family;
  private final GetPetOwnerUseCase owners;
  private final LockPetOwnerUseCase locks;

  @Override
  public void requireWriteAccess(Long memberId, Long petId) {
    owners.get(new GetPetOwnerQuery(petId)).orElseThrow(MediaPetNotFoundException::new);
    requireFamily(memberId, petId);
  }

  @Override
  public void requireLockedWriteAccess(Long memberId, Long petId) {
    locks.lock(new LockPetOwnerQuery(petId)).orElseThrow(MediaPetNotFoundException::new);
    requireFamily(memberId, petId);
  }

  private void requireFamily(Long memberId, Long petId) {
    if (!family.check(new CheckPetAccessQuery(petId, memberId)).canRecord())
      throw new MediaPetAccessDeniedException();
  }

  @Override
  public void requireProfileOwner(Long memberId, Long petId) {
    var owner = owners.get(new GetPetOwnerQuery(petId)).orElseThrow(MediaPetNotFoundException::new);
    if (!owner.ownerMemberId().equals(memberId)) throw new MediaPetAccessDeniedException();
  }
}
