package com.pawmento.diary.application.port.in;

public interface GetDiaryCalendarUseCase {
  GetDiaryCalendarResult get(GetDiaryCalendarQuery query);
}
