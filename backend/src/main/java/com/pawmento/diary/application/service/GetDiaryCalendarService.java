package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.exception.InvalidDiaryQueryException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDiaryCalendarService implements GetDiaryCalendarUseCase {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryCalendarPort entries;

  @Override
  @Transactional(readOnly = true)
  public GetDiaryCalendarResult get(GetDiaryCalendarQuery query) {
    if (query.month() == null) throw new InvalidDiaryQueryException();
    access.requireAccess(query.memberId(), query.petId());
    var start = query.month().atDay(1);
    var days =
        entries.days(query.petId(), start, start.plusMonths(1)).stream()
            .map(day -> new GetDiaryCalendarResult.Day(day.date(), day.count()))
            .toList();
    return new GetDiaryCalendarResult(query.petId(), query.month(), days);
  }
}
