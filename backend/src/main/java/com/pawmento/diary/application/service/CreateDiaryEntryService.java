package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateDiaryEntryService implements CreateDiaryEntryUseCase {
  private final CheckDiaryPetAccessPort access;
  private final SaveDiaryEntryPort save;
  private final java.time.Clock clock;

  @Override
  @Transactional
  public GetDiaryEntryResult create(CreateDiaryEntryCommand c) {
    access.requireAccess(c.memberId(), c.petId());
    return GetDiaryEntryResult.from(
        save.save(
            DiaryEntry.create(
                c.petId(), c.memberId(), c.content(), c.recordedDate(), clock.instant())));
  }
}
