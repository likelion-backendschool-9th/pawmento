package com.pawmento.question.adapter.out.access;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.family.application.port.in.*;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.question.application.exception.QuestionPetAccessDeniedException;
import com.pawmento.question.application.exception.QuestionPetNotFoundException;
import com.pawmento.question.domain.QuestionMode;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FamilyQuestionPetAccessAdapterTest {
  private final CheckPetAccessUseCase family = mock(CheckPetAccessUseCase.class);
  private final GetPetOwnerUseCase owners = mock(GetPetOwnerUseCase.class);
  private final LockPetOwnerUseCase locks = mock(LockPetOwnerUseCase.class);
  private final FamilyQuestionPetAccessAdapter adapter =
      new FamilyQuestionPetAccessAdapter(family, owners, locks);

  @Test
  @DisplayName("#31-S31 소유주 조회 뒤 이전이 끼어도 실제 가족 판정과 같은 스냅샷의 모드만 사용한다")
  void transferBetweenOwnerReadAndFamilyCheck() {
    var owner = new AtomicLong(1L);
    when(owners.get(new GetPetOwnerQuery(10L)))
        .thenAnswer(
            inv -> {
              var snapshot = new GetPetOwnerResult(owner.get(), "GARDEN");
              owner.set(2L); // 최초 소유주 조회 결과가 반환된 뒤 소유주 이전이 커밋된 상황
              return Optional.of(snapshot);
            });
    when(family.check(new CheckPetAccessQuery(10L, 3L)))
        .thenAnswer(
            inv -> {
              assertThat(owner.get()).isEqualTo(2L);
              return new CheckPetAccessResult("FAMILY");
            });

    assertThat(adapter.currentMode(3L, 10L)).isEqualTo(QuestionMode.GARDEN);
    verify(owners, times(1)).get(new GetPetOwnerQuery(10L));
    verifyNoInteractions(locks);
  }

  @Test
  @DisplayName("#31-S31 쓰기 모드는 pet 잠금 결과에서 읽고 잠금 뒤 가족 권한을 확인한다")
  void writeUsesLockedSnapshot() {
    when(locks.lock(new LockPetOwnerQuery(10L)))
        .thenReturn(Optional.of(new GetPetOwnerResult(2L, "ACTIVE")));
    when(family.check(new CheckPetAccessQuery(10L, 3L)))
        .thenReturn(new CheckPetAccessResult("FAMILY"));

    assertThat(adapter.currentModeForWrite(3L, 10L)).isEqualTo(QuestionMode.ACTIVE);
    var order = inOrder(locks, family);
    order.verify(locks).lock(new LockPetOwnerQuery(10L));
    order.verify(family).check(new CheckPetAccessQuery(10L, 3L));
    verifyNoInteractions(owners);
  }

  @Test
  @DisplayName("#31-S31 모드가 있어도 비가족은 403이고 없는 아이는 404다")
  void stillChecksRealRequester() {
    when(owners.get(new GetPetOwnerQuery(10L)))
        .thenReturn(Optional.of(new GetPetOwnerResult(1L, "ACTIVE")));
    when(family.check(new CheckPetAccessQuery(10L, 3L)))
        .thenReturn(new CheckPetAccessResult("NONE"));
    assertThatThrownBy(() -> adapter.currentMode(3L, 10L))
        .isInstanceOf(QuestionPetAccessDeniedException.class);
    when(owners.get(new GetPetOwnerQuery(11L))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> adapter.currentMode(3L, 11L))
        .isInstanceOf(QuestionPetNotFoundException.class);
    verify(family, never()).check(new CheckPetAccessQuery(11L, 3L));
  }
}
