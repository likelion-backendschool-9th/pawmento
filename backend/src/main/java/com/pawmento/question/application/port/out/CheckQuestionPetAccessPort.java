package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionMode;

public interface CheckQuestionPetAccessPort {
  QuestionMode currentMode(Long memberId, Long petId);
}
