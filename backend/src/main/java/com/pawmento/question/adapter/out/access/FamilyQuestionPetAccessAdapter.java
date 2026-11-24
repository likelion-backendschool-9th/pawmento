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
  private final GetPetUseCase pets;

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

  private QuestionMode modeAfterAccess(Long memberId, Long petId, GetPetOwnerResult owner) {
    if (!family.check(new CheckPetAccessQuery(petId, memberId)).canRecord())
      throw new QuestionPetAccessDeniedException();
    // 실제 요청자의 가족 권한 확인 후 기존 소유주 전용 조회에서 mode만 읽는다.
    // 소유주 ID를 명령의 작성자나 실제 요청자로 사용하지 않는다.
    return QuestionMode.valueOf(pets.get(new GetPetQuery(owner.ownerMemberId(), petId)).mode());
  }
}
