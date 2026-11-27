package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionMode;

public interface CheckQuestionPetWriteAccessPort {
  record DeletionAccess(boolean owner, QuestionMode mode) {}

  /** pet 잠금 이후 확인한 가족 접근·소유주·현재 모드다. */
  DeletionAccess accessForDeletion(Long memberId, Long petId);

  QuestionMode currentModeForWrite(Long memberId, Long petId);
}
