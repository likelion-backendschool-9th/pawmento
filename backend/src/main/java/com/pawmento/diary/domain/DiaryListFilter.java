package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.InvalidDiaryQueryException;
import java.time.LocalDate;

public record DiaryListFilter(LocalDate date, LocalDate from, LocalDate to, int page, int size) {
  public DiaryListFilter {
    if (page < 0
        || size < 1
        || size > 100
        || (date != null && (from != null || to != null))
        || (from != null && to != null && from.isAfter(to))) throw new InvalidDiaryQueryException();
  }

  public LocalDate fromDate() {
    return date == null ? from : date;
  }

  public LocalDate toDate() {
    return date == null ? to : date;
  }

  public long offset() {
    return (long) page * size;
  }
}
