package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EditDiaryEntryService implements EditDiaryEntryUseCase {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryEntryPort load;
  private final SaveDiaryEntryPort save;
  private final java.time.Clock clock;

  @Override
  @Transactional
  public GetDiaryEntryResult edit(EditDiaryEntryCommand c) {
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.recordId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    entry.edit(c.memberId(), c.content(), c.recordedDate(), clock.instant());
    return GetDiaryEntryResult.from(save.save(entry));
  }
}
