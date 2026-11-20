package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.map.application.port.in.ApplyDiaryLocationCommand;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationCommand;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;

class GetDiaryLocationServiceTest {
  @Test
  @DisplayName("#44-S6 잘못된 일기 ID와 내부 명령 입력은 공통 규칙 위반 예외로 거절한다")
  void rejectsInvalidIdentityBeforeStorage() {
    var store = mock(DiaryLocationStorePort.class);
    var service = new GetDiaryLocationService(store);
    for (Long id : new Long[] {null, 0L, -1L}) {
      assertThatThrownBy(() -> service.get(id)).isInstanceOf(RuleViolationException.class);
      assertThatThrownBy(() -> new ApplyDiaryLocationCommand(id, 1L, 1L, 0, null))
          .isInstanceOf(RuleViolationException.class);
    }
    assertThatThrownBy(() -> new ReconcileDiaryLocationCommand(1L, 1L, 1L, -1, null, null))
        .isInstanceOf(RuleViolationException.class);
    verifyNoInteractions(store);
  }
}
