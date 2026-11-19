package com.pawmento.diary.application.port.in;

import java.time.YearMonth;

public record GetDiaryCalendarQuery(Long memberId, Long petId, YearMonth month) {}
