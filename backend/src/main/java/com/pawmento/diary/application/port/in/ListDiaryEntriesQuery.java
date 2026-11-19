package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryListFilter;
import java.time.LocalDate;

public record ListDiaryEntriesQuery(Long memberId, Long petId, DiaryListFilter filter) {
  public static ListDiaryEntriesQuery of(
      Long memberId, Long petId, LocalDate date, LocalDate from, LocalDate to, int page, int size) {
    return new ListDiaryEntriesQuery(
        memberId, petId, new DiaryListFilter(date, from, to, page, size));
  }
}
