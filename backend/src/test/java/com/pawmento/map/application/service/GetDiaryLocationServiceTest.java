package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.DiaryLocationContractViolation;
import com.pawmento.map.application.port.in.ApplyDiaryLocationCommand;
import com.pawmento.map.application.port.in.ReconcileDiaryLocationCommand;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;

class GetDiaryLocationServiceTest {
  @Test
  @DisplayName("#44-S6 잘못된 일기 ID와 내부 명령 입력은 내부 계약 위반 예외로 거절한다")
  void rejectsInvalidIdentityBeforeStorage() {
    var store = mock(DiaryLocationStorePort.class);
    var service = new GetDiaryLocationService(store);
    for (Long id : new Long[] {null, 0L, -1L}) {
      assertThatThrownBy(() -> service.get(id)).isInstanceOf(DiaryLocationContractViolation.class);
      assertThatThrownBy(() -> new ApplyDiaryLocationCommand(id, 1L, 1L, 0, null))
          .isInstanceOf(DiaryLocationContractViolation.class);
    }
    assertThatThrownBy(() -> new ReconcileDiaryLocationCommand(1L, 1L, 1L, -1, null, null))
        .isInstanceOf(DiaryLocationContractViolation.class);
    verifyNoInteractions(store);
  }
  @Test
  @DisplayName("#44-S6 다른 일기의 관측값은 계약 위반이고 손상 관측값은 재검증하지 않는다")
  void internalContractIsServerError() throws Exception {
    var raw = new com.pawmento.map.application.port.in.DiaryLocationSnapshot(1L, 1L, 2L, 3L,
        Double.NaN, 999, "가".repeat(256), null, 4L, -1);
    org.assertj.core.api.Assertions.assertThat(DiaryLocationPortValues.state(raw).latitude()).isNaN();
    assertThatThrownBy(() -> new ReconcileDiaryLocationCommand(2L, 2L, 3L, 0, raw, null))
        .isInstanceOf(DiaryLocationContractViolation.class);
  }

}
