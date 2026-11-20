package com.pawmento.question.adapter.out.access;

import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.question.application.port.out.CheckQuestionPetAccessPort;
import com.pawmento.question.domain.QuestionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OwnerQuestionPetAccessAdapter implements CheckQuestionPetAccessPort {
  private final GetPetUseCase pets;

  public QuestionMode currentMode(Long memberId, Long petId) {
    return QuestionMode.valueOf(pets.get(new GetPetQuery(memberId, petId)).mode());
  }
}
