package com.pawmento.diary.adapter.in.web;

import com.pawmento.diary.application.port.in.GetDiaryCalendarResult;
import java.time.LocalDate;
import java.util.List;

public record DiaryCalendarResponse(Long petId, String month, List<Day> days) {
  public record Day(LocalDate date, long count) {}

  static DiaryCalendarResponse from(GetDiaryCalendarResult result) {
    return new DiaryCalendarResponse(
        result.petId(),
        result.month().toString(),
        result.days().stream().map(day -> new Day(day.date(), day.count())).toList());
  }
}
