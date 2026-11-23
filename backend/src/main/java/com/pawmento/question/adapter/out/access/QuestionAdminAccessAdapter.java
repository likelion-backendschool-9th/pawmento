package com.pawmento.question.adapter.out.access;

import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.GetMemberQuery;
import com.pawmento.member.application.port.in.GetMemberUseCase;
import com.pawmento.question.application.exception.QuestionAdminDeniedException;
import com.pawmento.question.application.port.out.CheckQuestionAdminAccessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionAdminAccessAdapter implements CheckQuestionAdminAccessPort {
  private final GetMemberUseCase members;

  public void requireAdmin(Long memberId) {
    if (memberId == null || memberId <= 0) throw new QuestionAdminDeniedException();
    try {
      if (!"ADMIN".equals(members.get(new GetMemberQuery(memberId)).role()))
        throw new QuestionAdminDeniedException();
    } catch (MemberNotFoundException missing) {
      throw new QuestionAdminDeniedException();
    }
  }
}
