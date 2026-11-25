package com.pawmento.question.adapter.out.access;

import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionAnswerAccessAdapter implements LoadQuestionAuthorNamePort {
  private final GetMemberUseCase members;

  public String loadName(Long member) {
    String name = members.get(new GetMemberQuery(member)).nickname();
    if (name == null || name.isBlank()) {
      throw new StoredQuestionInvalidException(
          new IllegalStateException("회원의 작성자 이름 스냅샷이 비어 있습니다."));
    }
    return name;
  }
}
