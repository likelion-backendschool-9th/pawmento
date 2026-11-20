package com.pawmento.question.adapter.out.access;

import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.pet.application.port.in.GetPetForUpdateUseCase;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.QuestionMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionAnswerAccessAdapter
    implements CheckQuestionPetWriteAccessPort, LoadQuestionAuthorNamePort {
  private final GetPetForUpdateUseCase pets;
  private final GetMemberUseCase members;

  public QuestionMode currentModeForWrite(Long member, Long pet) {
    return QuestionMode.valueOf(pets.getForUpdate(new GetPetQuery(member, pet)).mode());
  }

  public String loadName(Long member) {
    return members.get(new GetMemberQuery(member)).nickname();
  }
}
