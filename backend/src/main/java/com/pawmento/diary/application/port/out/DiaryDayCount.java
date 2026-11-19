package com.pawmento.diary.application.port.out;

import java.time.LocalDate;

public record DiaryDayCount(LocalDate date, long count) {}
