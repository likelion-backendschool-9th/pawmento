package com.pawmento.diary.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.diary.domain.DiaryEntry;
import com.pawmento.pet.adapter.out.persistence.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.postgresql.util.*;
import org.springframework.dao.DataIntegrityViolationException;

class DiaryPersistenceFailureTest {
  DataIntegrityViolationException failure(String constraint, String state) {
    return new DataIntegrityViolationException(
        "db failure",
        new PSQLException(
            new ServerErrorMessage(
                "SERROR\0C" + state + "\0Mconstraint failed\0n" + constraint + "\0")));
  }

  @Test
  @DisplayName("#32-S22 diary는 다른 FK·CHECK 오류를 아이 없음으로 일괄 변환하지 않는다")
  void otherDiaryConstraints() {
    var rows = mock(DiaryEntryJpaRepository.class);
    var adapter = new DiaryEntryPersistenceAdapter(rows);
    var entry =
        DiaryEntry.create(
            1L, 2L, "일기", LocalDate.of(2026, 11, 17), Instant.parse("2026-11-17T15:00:00Z"));
    for (var error :
        new DataIntegrityViolationException[] {
          failure("another_fk", "23503"), failure("fk_diary_entries_pet", "23514")
        }) {
      when(rows.saveAndFlush(any())).thenThrow(error);
      assertThatThrownBy(() -> adapter.save(entry)).isSameAs(error);
      reset(rows);
    }
  }

  @Test
  @DisplayName("#32-S22 pet는 다른 FK·CHECK 오류를 기록 존재 충돌로 일괄 변환하지 않는다")
  void otherPetConstraints() {
    var rows = mock(PetJpaRepository.class);
    var adapter = new PetPersistenceAdapter(rows);
    for (var error :
        new DataIntegrityViolationException[] {
          failure("another_fk", "23001"), failure("fk_diary_entries_pet", "23514")
        }) {
      doThrow(error).when(rows).flush();
      assertThatThrownBy(() -> adapter.deleteById(1L)).isSameAs(error);
      reset(rows);
    }
  }

  @Test
  @DisplayName("#32-S22 pet의 알려진 삭제 제한은 공통 충돌 예외로 변환하며 원인을 보존한다")
  void petConstraintCause() {
    var rows = mock(PetJpaRepository.class);
    var adapter = new PetPersistenceAdapter(rows);
    for (String state : new String[] {"23001", "23503"}) {
      var error = failure("fk_diary_entries_pet", state);
      doThrow(error).when(rows).flush();
      assertThatThrownBy(() -> adapter.deleteById(1L))
          .isInstanceOf(com.pawmento.pet.application.exception.PetHasRecordsException.class)
          .isInstanceOf(com.pawmento.global.exception.StateConflictException.class)
          .hasCause(error);
      reset(rows);
    }
  }
}
