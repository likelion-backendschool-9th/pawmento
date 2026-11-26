package com.pawmento.question.adapter.out.access;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.member.application.exception.MemberNotFoundException;
import com.pawmento.member.application.port.in.*;
import com.pawmento.question.application.exception.QuestionAdminDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuestionAdminAccessAdapterTest {
  @Test
  @DisplayName("#31-S22 회원 조회 장애를 권한 거부로 숨기지 않고 원인을 보존한다")
  void preserveFailure() {
    var members = mock(GetMemberUseCase.class);
    var failure = new IllegalStateException("member storage unavailable");
    when(members.get(new GetMemberQuery(1L))).thenThrow(failure);
    assertThatThrownBy(() -> new QuestionAdminAccessAdapter(members).requireAdmin(1L)).isSameAs(failure);
  }
}
