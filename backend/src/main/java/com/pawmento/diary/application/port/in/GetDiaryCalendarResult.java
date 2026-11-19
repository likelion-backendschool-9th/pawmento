package com.pawmento.diary.application.port.in;

import java.time.*;
import java.util.List;

public record GetDiaryCalendarResult(Long petId, YearMonth month, List<Day> days) {
  public GetDiaryCalendarResult {
    days = List.copyOf(days);
  }

  public record Day(LocalDate date, long count) {}
}
