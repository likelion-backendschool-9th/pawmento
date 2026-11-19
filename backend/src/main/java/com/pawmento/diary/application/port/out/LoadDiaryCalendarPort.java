package com.pawmento.diary.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface LoadDiaryCalendarPort {
  List<DiaryDayCount> days(Long petId, LocalDate from, LocalDate before);
}
