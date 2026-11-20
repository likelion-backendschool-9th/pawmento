package com.pawmento.question.adapter.out.access;

import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.pet.application.port.in.GetPetForUpdateUseCase;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.question.application.exception.StoredQuestionInvalidException;
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
    String name = members.get(new GetMemberQuery(member)).nickname();
    if (name == null || name.isBlank()) {
      throw new StoredQuestionInvalidException(
          new IllegalStateException("회원의 작성자 이름 스냅샷이 비어 있습니다."));
    }
    return name;
  }
}
