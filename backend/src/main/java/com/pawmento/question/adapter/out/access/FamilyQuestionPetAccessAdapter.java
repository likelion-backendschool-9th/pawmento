package com.pawmento.question.adapter.out.access;

import com.pawmento.family.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.QuestionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FamilyQuestionPetAccessAdapter
    implements CheckQuestionPetAccessPort, CheckQuestionPetWriteAccessPort {
  private final CheckPetAccessUseCase family;
  private final GetPetOwnerUseCase owners;
  private final LockPetOwnerUseCase locks;

  public QuestionMode currentMode(Long memberId, Long petId) {
    var owner =
        owners.get(new GetPetOwnerQuery(petId)).orElseThrow(QuestionPetNotFoundException::new);
    return modeAfterAccess(memberId, petId, owner);
  }

  public QuestionMode currentModeForWrite(Long memberId, Long petId) {
    var owner =
        locks.lock(new LockPetOwnerQuery(petId)).orElseThrow(QuestionPetNotFoundException::new);
    return modeAfterAccess(memberId, petId, owner);
  }

  public DeletionAccess accessForDeletion(Long memberId, Long petId) {
    var owner =
        locks.lock(new LockPetOwnerQuery(petId)).orElseThrow(QuestionPetNotFoundException::new);
    var mode = modeAfterAccess(memberId, petId, owner);
    return new DeletionAccess(owner.ownerMemberId().equals(memberId), mode);
  }

  private QuestionMode modeAfterAccess(Long memberId, Long petId, GetPetOwnerResult owner) {
    if (!family.check(new CheckPetAccessQuery(petId, memberId)).canRecord())
      throw new QuestionPetAccessDeniedException();
    return QuestionMode.valueOf(owner.mode());
  }
}
