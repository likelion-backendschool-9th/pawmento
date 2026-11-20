package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionMode;

public interface CheckQuestionPetWriteAccessPort {
  QuestionMode currentModeForWrite(Long memberId, Long petId);
}
